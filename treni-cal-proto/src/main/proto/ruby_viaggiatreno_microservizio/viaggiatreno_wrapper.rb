#!/usr/bin/env ruby

require 'net/http'
require 'json'
require 'uri'

class ViaggiatrenoWrapper
  BASE_URL = 'http://www.viaggiatreno.it/infomobilita/resteasy/viaggiatreno'

  def initialize
    @logger = Logger.new(STDOUT)
    @logger.level = Logger::INFO
  end

  # Cerca stazioni per nome
  def find_stations(query)
    url = "#{BASE_URL}/cercaStazione/#{URI.encode_www_form_component(query)}"
    response = make_request(url)

    return [] unless response

    # L'API restituisce un array di stazioni
    stations = []
    if response.is_a?(Array)
      response.each do |station_data|
        stations << {
          id: station_data['id'],
          name: station_data['nomeLungo'] || station_data['nomeBreve'],
          code: station_data['codReg']
        }
      end
    end

    stations
  rescue => e
    @logger.error("Errore ricerca stazioni: #{e.message}")
    []
  end

  # Ottieni informazioni su un treno
  def get_train_info(train_number, station_code = 'S08409') # Default: Roma Termini
    url = "#{BASE_URL}/andamentoTreno/#{station_code}/#{train_number}"
    response = make_request(url)

    return nil unless response

    # Controlla se il treno esiste
    if response['numeroTreno'].to_i == 0
      @logger.warn("Treno #{train_number} non trovato")
      return nil
    end

    {
      numero: response['numeroTreno'],
      categoria: response['categoria'],
      origine: response['origine'],
      destinazione: response['destinazione'],
      status: extract_status(response),
      delay: extract_delay(response),
      last_station: extract_last_station(response),
      departed: !response['nonPartito'],
      cancelled: response['provvedimento'] == 1
    }
  rescue => e
    @logger.error("Errore info treno #{train_number}: #{e.message}")
    nil
  end

  # Ottieni solo lo status di un treno (più veloce)
  def get_train_status(train_number, station_code = 'S08409')
    info = get_train_info(train_number, station_code)
    return "Servizio temporaneamente non disponibile" unless info

    if info[:cancelled]
      return "Treno cancellato"
    elsif !info[:departed]
      return "Treno non ancora partito"
    else
      return info[:status] || "In viaggio"
    end
  end

  # Ottieni treni in partenza da una stazione
  def get_departures(station_code, datetime = nil)
    datetime ||= Time.now.strftime("%Y-%m-%dT%H:%M:%S")
    url = "#{BASE_URL}/partenze/#{station_code}/#{datetime}"
    response = make_request(url)

    return [] unless response && response.is_a?(Array)

    trains = []
    response.each do |train_data|
      trains << {
        numero: train_data['numeroTreno'],
        categoria: train_data['categoria'],
        destinazione: train_data['destinazione'],
        orario: train_data['orarioPartenza'],
        binario: train_data['binario'],
        ritardo: train_data['ritardo'] || 0
      }
    end

    trains
  rescue => e
    @logger.error("Errore partenze da #{station_code}: #{e.message}")
    []
  end

  private

  def make_request(url)
    @logger.info("Making request to: #{url}")

    uri = URI(url)
    http = Net::HTTP.new(uri.host, uri.port)
    http.open_timeout = 10
    http.read_timeout = 10

    request = Net::HTTP::Get.new(uri)
    request['User-Agent'] = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'

    response = http.request(request)

    if response.code == '200'
      JSON.parse(response.body)
    else
      @logger.error("HTTP Error: #{response.code} - #{response.body}")
      nil
    end
  rescue Net::TimeoutError
    @logger.error("Timeout per richiesta: #{url}")
    nil
  rescue JSON::ParserError => e
    @logger.error("Errore parsing JSON: #{e.message}")
    @logger.debug("Response body: #{response&.body}")
    nil
  rescue => e
    @logger.error("Errore generico: #{e.message}")
    nil
  end

  def extract_status(response)
    # Controlla vari campi per lo status
    if response['riprogrammazione'] && !response['riprogrammazione'].empty?
      return response['riprogrammazione']
    end

    if response['subTitle'] && !response['subTitle'].empty?
      return response['subTitle']
    end

    if response['provvedimento'] == 1
      return "Treno cancellato"
    end

    if response['nonPartito']
      return "Treno non ancora partito"
    end

    # Controlla se è in stazione
    if response['inStazione']
      last_detection = response['stazioneUltimoRilevamento']
      return "In stazione: #{last_detection}" if last_detection
    end

    "In viaggio"
  end

  def extract_delay(response)
    # Il ritardo può essere in diversi formati
    ritardo = response['ritardo']
    return 0 unless ritardo

    if ritardo.is_a?(Integer)
      return ritardo
    elsif ritardo.is_a?(String) && ritardo.match(/\d+/)
      return ritardo.to_i
    end

    # Controlla nei dettagli delle stazioni
    comp_ritardo = response['compRitardo']
    if comp_ritardo && comp_ritardo.last
      return comp_ritardo.last['ritardo'].to_i
    end

    0
  end

  def extract_last_station(response)
    response['stazioneUltimoRilevamento'] || response['origine']
  end
end

# Test se eseguito direttamente
if __FILE__ == $0
  require 'logger'

  puts "🚂 Test ViaggiatrenoWrapper"
  puts "=" * 50

  wrapper = ViaggiatrenoWrapper.new

  # Test ricerca stazioni
  puts "\n🏭 Test ricerca stazioni 'Roma':"
  stations = wrapper.find_stations("Roma")
  puts "Trovate #{stations.length} stazioni:"
  stations.each { |s| puts "  - #{s[:name]} (#{s[:id]})" }

  # Prima ottieni i treni in partenza da Roma Termini OGGI
  puts "\n🚆 Treni in partenza da Roma Termini:"
  departures = wrapper.get_departures("S08409")

  if departures.any?
    puts "Trovati #{departures.length} treni in partenza:"
    departures.first(5).each do |train|
      puts "  - #{train[:categoria]} #{train[:numero]} per #{train[:destinazione]} alle #{train[:orario]}"
    end

    # Testa i primi 3 treni reali
    puts "\n🚄 Test treni REALI in partenza oggi:"
    departures.first(3).each do |train_data|
      train_num = train_data[:numero]
      puts "\n🚄 Test treno REALE #{train_num}:"
      info = wrapper.get_train_info(train_num)

      if info
        puts "  ✅ Treno: #{info[:categoria]} #{info[:numero]}"
        puts "  📍 Status: #{info[:status]}"
        puts "  ⏰ Delay: #{info[:delay]} min"
        puts "  🏠 Last station: #{info[:last_station]}"
      else
        puts "  ❌ Nessuna informazione disponibile"
      end
    end
  else
    puts "  ❌ Nessun treno in partenza trovato"

    # Fallback: test con treni comuni che di solito esistono
    puts "\n🚄 Test con treni comuni:"
    common_trains = [8306, 8308, 8310] # Frecciarossa comuni
    common_trains.each do |train_num|
      puts "\n🚄 Test treno comune #{train_num}:"
      info = wrapper.get_train_info(train_num)

      if info
        puts "  ✅ Treno: #{info[:categoria]} #{info[:numero]}"
        puts "  📍 Status: #{info[:status]}"
        puts "  ⏰ Delay: #{info[:delay]} min"
        puts "  🏠 Last station: #{info[:last_station]}"
      else
        puts "  ❌ Nessuna informazione disponibile"
      end
    end
  end
end