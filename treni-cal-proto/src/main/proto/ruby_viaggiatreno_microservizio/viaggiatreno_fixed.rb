#!/usr/bin/env ruby

# Versione fixata della gem viaggiatreno per Windows
# Risolve il bug di File.open() usato su URL HTTP

require 'net/http'
require 'uri'
require 'json'
require 'openssl'

module Viaggiatreno
  # URL corretto trovato tramite i redirect
  CORRECT_BASE_URL = 'http://www.viaggiatreno.it/vt_pax_internet/mobile'

  class Station
    attr_accessor :id, :name, :region

    def initialize(id, name, region = nil)
      @id = id
      @name = name
      @region = region
    end
  end

  class Train
    attr_accessor :number, :category, :status, :delay, :last_station, :origin, :destination

    def initialize(number)
      @number = number.to_i
      @delay = 0
      @status = nil
      @origin = nil
      @destination = nil
      @category = nil
      update
    end

    def update
      scraper = Scraper.new
      scraper.update_train(self)
    end

    def delay
      @delay || 0
    end

    def status
      @status || "Informazioni non disponibili"
    end

    def last_station
      @last_station || @origin
    end
  end

  class Scraper
    def update_train(train)
      # Usa l'URL corretto che abbiamo trovato
      url = "#{CORRECT_BASE_URL}/numero?numeroTreno=#{train.number}&tipoRicerca=numero&lang=IT"

      begin
        puts "Tentativo con URL corretto: #{url}"
        response = make_http_request(url)

        if response && !response.empty?
          puts "Risposta ricevuta, lunghezza: #{response.length} caratteri"
          parse_train_response(train, response)
        else
          puts "Nessuna risposta o risposta vuota"
          try_rest_api_fallback(train)
        end
      rescue => e
        puts "Errore aggiornamento treno #{train.number}: #{e.message}"
        try_rest_api_fallback(train)
      end
    end

    def find_station(query)
      # Usa l'API REST che funziona
      url = "http://www.viaggiatreno.it/infomobilita/resteasy/viaggiatreno/cercaStazione/#{URI.encode_www_form_component(query)}"

      begin
        response = make_http_request(url)

        if response
          stations_data = JSON.parse(response)
          stations = []

          stations_data.each do |station_data|
            station = Station.new(
              station_data['id'],
              station_data['nomeLungo'] || station_data['nomeBreve'],
              station_data['regione']
            )
            stations << station
          end

          return stations
        end
      rescue JSON::ParserError => e
        puts "Errore parsing JSON stazioni: #{e.message}"
      rescue => e
        puts "Errore ricerca stazioni: #{e.message}"
      end

      []
    end

    private

    def make_http_request(url, max_redirects = 5)
      return nil if max_redirects <= 0

      uri = URI(url)

      # Configura HTTPS se necessario
      http = Net::HTTP.new(uri.host, uri.port)
      if uri.scheme == 'https'
        http.use_ssl = true
        http.verify_mode = OpenSSL::SSL::VERIFY_NONE  # Per evitare problemi SSL
      end

      http.open_timeout = 10
      http.read_timeout = 15

      request = Net::HTTP::Get.new(uri)
      request['User-Agent'] = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36'
      request['Accept'] = 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8'
      request['Accept-Language'] = 'it-IT,it;q=0.8,en-US;q=0.5,en;q=0.3'

      response = http.request(request)

      case response.code
      when '200'
        return response.body
      when '301', '302', '303', '307', '308'
        # Segui il redirect
        new_location = response['location']
        if new_location
          puts "Redirect #{response.code}: #{url} -> #{new_location}"
          # Se è un redirect relativo, costruisci l'URL completo
          if new_location.start_with?('/')
            new_location = "#{uri.scheme}://#{uri.host}#{new_location}"
          end
          return make_http_request(new_location, max_redirects - 1)
        else
          puts "Redirect senza location header"
          return nil
        end
      else
        puts "HTTP Error #{response.code}: #{response.body[0..200]}"
        return nil
      end
    rescue Net::TimeoutError
      puts "Timeout per richiesta: #{url}"
      nil
    rescue => e
      puts "Errore richiesta HTTP: #{e.message}"
      nil
    end

    def parse_train_response(train, html_response)
      # Fix encoding issues
      begin
        html_response = html_response.force_encoding('UTF-8')
        unless html_response.valid_encoding?
          html_response = html_response.force_encoding('ISO-8859-1').encode('UTF-8')
        end
      rescue Encoding::UndefinedConversionError
        html_response = html_response.encode('UTF-8', invalid: :replace, undef: :replace, replace: '')
      end

      puts "Parsing HTML response for train #{train.number}..."

      # Estrai il numero del treno e categoria dall'header <h1>
      if match = html_response.match(/<h1>\s*([^<]+?)\s*<\/h1>/im)
        train_info = match[1].strip
        if !train_info.empty?
          puts "Train header found: #{train_info}"

          # Esempi: "IC 503", "FR 9001", "REG 8306"
          if train_parts = train_info.match(/^(\w+)\s+(\d+)$/i)
            train.category = train_parts[1]
            # Non sovrascrivere il numero del treno se è già corretto
          end
        else
          puts "Train header found but empty"
        end
      else
        puts "No train header found in HTML"

        # Prova pattern alternativi se non trova <h1>
        # Cerca nel title o altri tag
        if title_match = html_response.match(/<title>([^<]+)<\/title>/im)
          title_text = title_match[1].strip
          puts "Found title: #{title_text}"

          # Il title spesso contiene "Scheda treno" o simili
          # Prova a estrarre info dal contenuto
          if train_match = title_text.match(/(IC|FR|NTV|REG)\s*(\d+)/i)
            train.category = train_match[1]
            puts "Extracted category from title: #{train.category}"
          end
        end
      end

      # Estrai origine e destinazione dai tag <h2>
      h2_matches = html_response.scan(/<h2>\s*([^<]+?)\s*<\/h2>/im)
      if h2_matches.length >= 2
        train.origin = h2_matches[0][0].strip
        train.destination = h2_matches[1][0].strip
        puts "Origin: #{train.origin}"
        puts "Destination: #{train.destination}"
      elsif h2_matches.length == 1
        # A volte c'è solo una destinazione
        train.destination = h2_matches[0][0].strip
        puts "Destination: #{train.destination}"
      end

      # Estrai informazioni di status dalla sezione "evidenziato"
      # Pattern migliorato per catturare tutto il contenuto, anche multi-riga
      status_pattern = /<div[^>]*class[^>]*evidenziato[^>]*>.*?<strong[^>]*>(.*?)<\/strong>.*?<\/div>/im

      if match = html_response.match(status_pattern)
        status_section = match[1]

        # Pulisci HTML: rimuovi tag, unisci righe, decodifica entità
        clean_status = status_section
                         .gsub(/<br\s*\/?>/, ' ')              # Converti <br> in spazi
                         .gsub(/<[^>]+>/, ' ')                 # Rimuovi tutti i tag HTML
                         .gsub(/&#039;/, "'")                  # Decodifica apostrofi
                         .gsub(/&quot;/, '"')                  # Decodifica virgolette
                         .gsub(/&amp;/, '&')                   # Decodifica &
                         .gsub(/\s+/, ' ')                     # Unifica spazi multipli
                         .strip                                # Rimuovi spazi iniziali/finali

        puts "Status section raw: #{clean_status}"

        # Parse diversi tipi di status con pattern più precisi
        case clean_status.downcase
        when /non.*ancora.*partito/
          train.status = "Non ancora partito"
          train.delay = 0
        when /arrivato.*?con\s+(\d+)\s+minuti?\s+di\s+ritardo/
          delay_minutes = $1.to_i
          train.delay = delay_minutes
          train.status = "Arrivato con #{delay_minutes} minuti di ritardo"
        when /partito.*?con\s+(\d+)\s+minuti?\s+di\s+ritardo/
          delay_minutes = $1.to_i
          train.delay = delay_minutes
          train.status = "Partito con #{delay_minutes} minuti di ritardo"
        when /viaggio.*?con\s+(\d+)\s+minuti?\s+di\s+ritardo/
          delay_minutes = $1.to_i
          train.delay = delay_minutes
          train.status = "In viaggio con #{delay_minutes} minuti di ritardo"
        when /ritardo\s+di\s+(\d+)\s+minuti?/
          delay_minutes = $1.to_i
          train.delay = delay_minutes
          train.status = "Ritardo di #{delay_minutes} minuti"
        when /in\s+orario/
          train.delay = 0
          train.status = "In orario"
        when /arrivato/
          train.delay = 0
          train.status = "Arrivato in orario"
        when /partito/
          train.delay = 0
          train.status = "Partito in orario"
        when /viaggio/
          train.status = "In viaggio"
          train.delay = 0
        when /cancellato/
          train.status = "Cancellato"
        when /soppresso/
          train.status = "Soppresso"
        else
          # Se non riconosciamo il pattern, usa il testo pulito
          train.status = clean_status.empty? ? "Stato non disponibile" : clean_status
          train.delay = 0
        end

        puts "Parsed status: #{train.status}, delay: #{train.delay}"
      else
        puts "No status section found"
        # Cerca pattern alternativi nel testo generale
        if html_response.match(/non.*circolazione/i) || html_response.match(/non.*possibile/i)
          train.status = "Treno non trovato"
        else
          train.status = "Informazioni non disponibili"
          train.delay = 0
        end
      end

      # Se non abbiamo trovato origine/destinazione nell'HTML, prova fallback
      # MA non sovrascrivere uno status valido
      if (train.origin.nil? || train.destination.nil?) &&
         (train.status.nil? || train.status == "Informazioni non disponibili")
        puts "Missing origin/destination AND no valid status, trying REST API fallback..."
        try_rest_api_fallback(train)
      elsif train.origin.nil? || train.destination.nil?
        puts "Missing origin/destination but have valid status: #{train.status}"
        # Prova il fallback ma non lasciare che sovrascriva lo status
        original_status = train.status
        original_delay = train.delay
        try_rest_api_fallback(train)
        # Se il fallback non ha migliorato le cose, ripristina lo status originale
        if train.status == "Servizio temporaneamente non disponibile" && original_status && original_status != "Servizio temporaneamente non disponibile"
          puts "Restoring original status: #{original_status}"
          train.status = original_status
          train.delay = original_delay
        end
      end
    end

    def try_rest_api_fallback(train)
      # Prova con diversi endpoint REST come fallback
      rest_urls = [
        "http://www.viaggiatreno.it/infomobilita/resteasy/viaggiatreno/andamentoTreno/S08409/#{train.number}",
        "http://www.viaggiatreno.it/infomobilita/resteasy/viaggiatreno/andamentoTreno/S01700/#{train.number}",
      ]

      rest_urls.each do |url|
        begin
          response = make_http_request(url)
          next unless response

          data = JSON.parse(response)
          if data && data['numeroTreno'] && data['numeroTreno'].to_i > 0
            parse_rest_response(train, data)
            return
          end
        rescue JSON::ParserError
          # Non è JSON valido, continua
        rescue => e
          # Errore generico, continua
        end
      end

      # Se tutti i fallback falliscono
      set_train_unavailable(train)
    end

    def parse_rest_response(train, data)
      train.status = extract_status_from_rest(data)
      train.delay = extract_delay_from_rest(data)
      train.origin = data['origine']
      train.destination = data['destinazione']
      train.last_station = data['stazioneUltimoRilevamento'] || train.origin
    end

    def extract_status_from_rest(data)
      if data['riprogrammazione'] && !data['riprogrammazione'].empty?
        return data['riprogrammazione']
      end

      if data['provvedimento'] == 1
        return "Treno cancellato"
      end

      if data['nonPartito']
        return "Treno non ancora partito"
      end

      if data['inStazione']
        station = data['stazioneUltimoRilevamento']
        return "In stazione: #{station}" if station
      end

      "In viaggio"
    end

    def extract_delay_from_rest(data)
      ritardo = data['ritardo']
      return 0 unless ritardo

      if ritardo.is_a?(Integer)
        return ritardo
      elsif ritardo.is_a?(String) && ritardo.match(/\d+/)
        return ritardo.to_i
      end

      if comp_ritardo = data['compRitardo']&.last
        return comp_ritardo['ritardo'].to_i
      end

      0
    end

    def set_train_unavailable(train)
      train.status = "Servizio temporaneamente non disponibile"
      train.delay = 0
    end
  end

  # Metodi di convenienza del modulo
  def self.find_station(query)
    scraper = Scraper.new
    scraper.find_station(query)
  end
end

# Classi globali per compatibilità con la gem originale
class Train < Viaggiatreno::Train
end

class Station < Viaggiatreno::Station
end

# Test se eseguito direttamente
if __FILE__ == $0
  puts "🚂 Test Viaggiatreno Fixed"
  puts "=" * 40

  # Test ricerca stazioni
  puts "\n🏭 Test ricerca stazioni:"
  stations = Viaggiatreno.find_station("Roma")
  puts "Trovate #{stations.length} stazioni:"
  stations.first(3).each { |s| puts "  - #{s.name} (#{s.id})" }

  # Test treni
  puts "\n🚄 Test treni:"
  [502, 504, 9001, 8306].each do |train_num|
    puts "\nTreno #{train_num}:"
    begin
      train = Train.new(train_num)
      puts "  Status: #{train.status}"
      puts "  Delay: #{train.delay} min"
      puts "  Origin: #{train.origin}"
      puts "  Destination: #{train.destination}"
      puts "  Category: #{train.category}"
    rescue => e
      puts "  Errore: #{e.message}"
    end
  end
end