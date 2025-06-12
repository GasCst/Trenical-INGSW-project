#!/usr/bin/env ruby

this_dir = File.expand_path(File.dirname(__FILE__))
$LOAD_PATH.unshift(this_dir) unless $LOAD_PATH.include?(this_dir)

require 'grpc'
require 'sqlite3'
require 'treni_pb'
require 'viaggiatreno_service_pb'
require 'viaggiatreno_service_services_pb'
require 'open-uri'
require 'net/http'
require 'nokogiri'
require 'yaml'
require 'timeout'

# Setup simple logging with better formatting
def log(level, message)
  timestamp = Time.now.strftime('%Y-%m-%d %H:%M:%S')
  puts "[#{timestamp}] #{level.upcase}: #{message}"
end

log('info', 'Starting Ruby Viaggiatreno Server...')

# Enhanced HTTP client with retry logic
module HTTPClient
  RETRY_ATTEMPTS = 3
  RETRY_DELAY = 1.0
  REQUEST_TIMEOUT = 30

  def self.get_with_retry(uri_string, headers = {})
    uri = URI.parse(uri_string)

    RETRY_ATTEMPTS.times do |attempt|
      begin
        return Timeout::timeout(REQUEST_TIMEOUT) do
          http = Net::HTTP.new(uri.host, uri.port)
          http.use_ssl = (uri.scheme == 'https')
          http.open_timeout = 10
          http.read_timeout = 20

          request = Net::HTTP::Get.new(uri.request_uri)
          request['User-Agent'] = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
          headers.each { |k, v| request[k] = v }

          response = http.request(request)

          if response.is_a?(Net::HTTPSuccess)
            return response.body
          elsif response.is_a?(Net::HTTPRedirection)
            # Handle redirects
            new_uri = URI.parse(response['location'])
            uri = uri.merge(new_uri)
            next
          else
            raise "HTTP #{response.code}: #{response.message}"
          end
        end
      rescue => e
        log('warn', "HTTP request attempt #{attempt + 1}/#{RETRY_ATTEMPTS} failed: #{e.message}")

        if attempt < RETRY_ATTEMPTS - 1
          sleep(RETRY_DELAY * (attempt + 1))
        else
          raise e
        end
      end
    end
  end
end

# Enhanced Kernel.open with better error handling
module Kernel
  alias_method :original_kernel_open_for_patch, :open

  def open(name, *rest, &block)
    if name.is_a?(String) && (name.start_with?('http://') || name.start_with?('https://'))
      begin
        body = HTTPClient.get_with_retry(name)
        string_io = StringIO.new(body)

        if block_given?
          begin
            yield string_io
          ensure
            string_io.close
          end
        else
          return string_io
        end
      rescue => e
        log('error', "Failed to fetch URL #{name}: #{e.message}")
        raise
      end
    else
      original_kernel_open_for_patch(name, *rest, &block)
    end
  end
end

# Load viaggiatreno_fixed
begin
  require_relative 'viaggiatreno_fixed'
  log('info', 'Viaggiatreno Fixed loaded successfully')
rescue LoadError => e
  log('fatal', 'Failed to load viaggiatreno_fixed.rb')
  log('fatal', e.message)
  exit 1
rescue => e
  log('error', "Unexpected error loading viaggiatreno_fixed: #{e.message}")
  exit 1
end

class ViaggiatrenoServer < Trenical::RubyViaggiatreno::ViaggiatrenoService::Service

  def initialize
    log('info', 'Initializing ViaggiatrenoServer...')

    @request_count = 0
    @error_count = 0
    @start_time = Time.now

    # Initialize database connection with retry
    initialize_database_with_retry

    # Initialize caches
    @station_cache = {}
    @train_cache = {}
    @cache_ttl = 300 # 5 minutes

    # Preload station cache for faster searches
    preload_station_cache

    log('info', 'ViaggiatrenoServer initialized successfully')
  end

  private

  def initialize_database_with_retry
    attempts = 0
    max_attempts = 3

    begin
      attempts += 1

      # Calculate path to project root - more robust path detection
      possible_paths = [
        File.expand_path("../../../../../", __dir__),
        File.expand_path("../../../../", __dir__),
        File.expand_path("../../../", __dir__),
        File.expand_path("../../", __dir__),
        File.expand_path("../", __dir__),
        Dir.pwd
      ]

      db_path = nil
      possible_paths.each do |path|
        candidate = File.join(path, "trenical.db")
        if File.exist?(candidate)
          db_path = candidate
          break
        end
      end

      if db_path.nil?
        raise "Database file 'trenical.db' not found in any of: #{possible_paths.join(', ')}"
      end

      @db = SQLite3::Database.new(db_path)
      @db.results_as_hash = true

      # Enable optimizations and WAL mode
      @db.execute("PRAGMA journal_mode = WAL")
      @db.execute("PRAGMA synchronous = NORMAL")
      @db.execute("PRAGMA cache_size = -32000") # 32MB cache
      @db.execute("PRAGMA temp_store = MEMORY")
      @db.execute("PRAGMA mmap_size = 268435456") # 256MB mmap

      # Test the connection
      result = @db.execute("SELECT COUNT(*) as count FROM stations").first
      station_count = result['count']

      log('info', "SQLite connection established: #{db_path}")
      log('info', "Database contains #{station_count} stations")

      if station_count == 0
        log('warn', "Database appears to be empty. Please run GTFSImporter first.")
      end

    rescue SQLite3::Exception => e
      log('error', "SQLite error (attempt #{attempts}/#{max_attempts}): #{e.message}")

      if attempts < max_attempts
        log('info', "Retrying database connection in 2 seconds...")
        sleep(2)
        retry
      else
        log('fatal', "Failed to connect to SQLite after #{max_attempts} attempts")
        exit 1
      end
    rescue => e
      log('fatal', "Database initialization failed: #{e.message}")
      log('fatal', e.backtrace.join("\n"))
      exit 1
    end
  end

  def preload_station_cache
    log('info', 'Preloading station cache...')

    begin
      sql = "SELECT stop_id, stop_name, stop_code, stop_lat, stop_lon, location_type FROM stations ORDER BY stop_name"
      results = @db.execute(sql)

      results.each do |row|
        station_info = {
          id: row['stop_id'],
          name: row['stop_name'],
          code: row['stop_code'],
          lat: row['stop_lat'],
          lon: row['stop_lon'],
          location_type: row['location_type']
        }

        # Cache by multiple keys for faster lookup
        @station_cache[row['stop_id']] = station_info
        @station_cache[row['stop_name'].to_s.downcase] = station_info if row['stop_name']
        @station_cache[row['stop_code'].to_s.downcase] = station_info if row['stop_code']
      end

      log('info', "Station cache preloaded: #{results.length} stations")

    rescue SQLite3::Exception => e
      log('error', "Error preloading station cache: #{e.message}")
    end
  end

  public

  def search_stations(request, _call)
    @request_count += 1
    search_query = request.search_query.to_s.strip.downcase

    log('info', "SearchStations request ##{@request_count}: '#{search_query}'")

    if search_query.length < 2
      log('warn', 'Search query too short (minimum 2 characters)')
      return ::Proto::StationListResponse.new(stations: [])
    end

    begin
      # Enhanced SQL query with better relevance scoring
      sql = """
        SELECT DISTINCT
          stop_id,
          stop_name,
          stop_code,
          location_type,
          CASE
            WHEN LOWER(stop_name) = ? THEN 100
            WHEN LOWER(stop_name) LIKE ? THEN 90
            WHEN LOWER(stop_code) = ? THEN 85
            WHEN LOWER(stop_name) LIKE ? THEN 70
            WHEN LOWER(stop_code) LIKE ? THEN 60
            ELSE 50
          END as relevance_score
        FROM stations
        WHERE
          (LOWER(stop_name) LIKE ? OR LOWER(stop_code) LIKE ?)
          AND (location_type IS NULL OR location_type IN (0, 1))
        ORDER BY
          relevance_score DESC,
          LENGTH(stop_name) ASC,
          stop_name ASC
        LIMIT 25
      """

      # Prepare search patterns
      exact_match = search_query
      starts_with = "#{search_query}%"
      contains = "%#{search_query}%"

      start_time = Time.now
      results = @db.execute(sql,
                            exact_match, starts_with, exact_match, contains, contains, contains, contains)
      query_time = ((Time.now - start_time) * 1000).round(2)

      log('info', "Found #{results.length} stations in #{query_time}ms")

      # Convert to protobuf stations
      proto_stations = results.map do |row|
        station_name = row['stop_name'].to_s

        # Enhance station name with code if available
        if row['stop_code'] && !row['stop_code'].empty?
          enhanced_name = "#{station_name} (#{row['stop_code']})"
        else
          enhanced_name = station_name
        end

        # Add location type indicator
        case row['location_type']
        when 1
          enhanced_name += " 🚉"
        when 0
          enhanced_name += " 🚏"
        end

        ::Proto::Station.new(
          id: row['stop_id'].to_s,
          name: enhanced_name
        )
      end

      ::Proto::StationListResponse.new(stations: proto_stations)

    rescue SQLite3::Exception => e
      @error_count += 1
      log('error', "Database error in search_stations: #{e.message}")
      ::Proto::StationListResponse.new(stations: [])
    rescue => e
      @error_count += 1
      log('error', "Unexpected error in search_stations: #{e.class} - #{e.message}")
      log('debug', e.backtrace.join("\n")) if e.backtrace
      ::Proto::StationListResponse.new(stations: [])
    end
  end

  def get_train_realtime_status(request, _call)
    @request_count += 1
    train_number_str = request.train_number.to_s.strip

    log('info', "GetTrainRealtimeStatus request ##{@request_count}: #{train_number_str}")

    unless train_number_str.match?(/^\d{3,5}$/)
      return create_error_response(train_number_str, "Numero treno non valido")
    end

    response = Trenical::RubyViaggiatreno::TrainStatusResponse.new(
      found: false,
      train_number: train_number_str
    )

    begin
      train_info = find_train_in_gtfs(train_number_str)
      log('info', "Querying Viaggiatreno API for: #{train_number_str}")

      # Usa la nuova versione fixed
      vt_train = nil
      begin
        vt_train = Train.new(train_number_str.to_i)
        log('info', "Train object created successfully")
      rescue => e
        log('error', "Train creation failed: #{e.message}")
        log('debug', e.backtrace.join("\n")) if e.backtrace
        response.error_message = "Impossibile creare oggetto treno: #{e.message}"
        return response
      end

      if vt_train && vt_train.status &&
         vt_train.status != "Informazioni non disponibili" &&
         vt_train.status != "Servizio temporaneamente non disponibile"

        log('info', "Train found: #{train_number_str}")

        response.found = true

        # Migliora il messaggio di status
        status_message = vt_train.status

        # Converti status specifici in messaggi più informativi
        case status_message
        when /^\d{2}:\d{2}$/  # Orario come "09:24", "08:56"
          status_message = "Arrivo previsto alle #{status_message}"
        when "Non ancora partito"
          status_message = "Il treno non è ancora partito"
        when /^In stazione:/
          status_message = vt_train.status  # Mantieni così com'è
        when /arrivato.*ritardo/i
          status_message = vt_train.status  # Mantieni così com'è
        when /partito.*ritardo/i
          status_message = vt_train.status  # Mantieni così com'è
        end

        response.train_status_description = status_message
        response.delay_minutes = vt_train.delay || 0
        response.train_category = vt_train.category || extract_train_category_from_info(train_info)
        response.last_detected_station = vt_train.last_station || "Nessun aggiornamento"
        response.last_detection_time = Time.now.strftime("%H:%M")

        # Usa i dati dal parsing HTML se disponibili
        if vt_train.origin && vt_train.destination
          response.origin_station = vt_train.origin
          response.destination_station = vt_train.destination
        elsif train_info
          response.origin_station = train_info['origin_station'] || "Partenza"
          response.destination_station = train_info['destination_station'] || "Arrivo"
        else
          response.origin_station = "Partenza"
          response.destination_station = "Arrivo"
        end

        log('info', "Status: #{response.train_status_description}, Delay: #{response.delay_minutes} min")
      else
        log('warn', "Train object created but no valid data available: status='#{vt_train&.status}'")

        # Provide more specific error messages
        if vt_train&.status == "Servizio temporaneamente non disponibile"
          response.error_message = "Servizio Viaggiatreno temporaneamente non disponibile per il treno #{train_number_str}"
        elsif vt_train&.status == "Informazioni non disponibili"
          response.error_message = "Treno #{train_number_str}: informazioni non disponibili"
        else
          response.error_message = "Treno #{train_number_str}: dati non disponibili (status: #{vt_train&.status})"
        end
      end

    rescue => e
      @error_count += 1
      log('error', "Error processing train #{train_number_str}: #{e.class} - #{e.message}")
      log('debug', e.backtrace.join("\n")) if e.backtrace
      response.error_message = "Errore nel recupero dati: #{e.message}"
    end

    log('info', "Response for #{train_number_str}: Found=#{response.found}")
    response
  end

  private

  def find_train_in_gtfs(train_number)
    begin
      sql = """
        SELECT DISTINCT
          t.trip_short_name,
          r.route_short_name,
          r.route_long_name,
          dep_st.stop_name as origin_station,
          arr_st.stop_name as destination_station,
          t.trip_headsign
        FROM trips t
        JOIN routes r ON t.route_id = r.route_id
        JOIN stop_times dep ON t.trip_id = dep.trip_id
        JOIN stop_times arr ON t.trip_id = arr.trip_id
        JOIN stations dep_st ON dep.stop_id = dep_st.stop_id
        JOIN stations arr_st ON arr.stop_id = arr_st.stop_id
        WHERE (
          t.trip_short_name LIKE '%' || ? || '%'
          OR r.route_short_name LIKE '%' || ? || '%'
        )
        AND dep.stop_sequence = (SELECT MIN(stop_sequence) FROM stop_times WHERE trip_id = t.trip_id)
        AND arr.stop_sequence = (SELECT MAX(stop_sequence) FROM stop_times WHERE trip_id = t.trip_id)
        LIMIT 1
      """

      results = @db.execute(sql, train_number, train_number)
      result = results.first

      if result
        log('info', "Found GTFS info for train #{train_number}: #{result['trip_short_name']}")
      end

      result

    rescue SQLite3::Exception => e
      log('error', "GTFS query error for train #{train_number}: #{e.message}")
      nil
    end
  end

  def extract_train_category_from_info(train_info)
    return "" unless train_info && train_info['trip_short_name']

    trip_name = train_info['trip_short_name'].to_s

    # Estrai categoria dal nome del trip
    if trip_name.match(/^(\w+)/i)
      return $1.upcase
    end

    ""
  end

  def extract_train_category(train_name)
    return "" unless train_name

    categories = {
      'frecciarossa' => 'Frecciarossa',
      'frecciargento' => 'Frecciargento',
      'frecciabianca' => 'Frecciabianca',
      'intercity' => 'Intercity',
      'regionale' => 'Regionale',
      'italo' => 'Italo',
      'ic' => 'IC',
      'fr' => 'FR'
    }

    train_name_lower = train_name.downcase
    categories.each do |key, value|
      return value if train_name_lower.include?(key)
    end

    train_name.split.first || ""
  end

  def create_error_response(train_number, error_message)
    Trenical::RubyViaggiatreno::TrainStatusResponse.new(
      found: false,
      train_number: train_number,
      error_message: error_message
    )
  end
end

# Enhanced signal handlers for graceful shutdown
def setup_signal_handlers(server)
  ['TERM', 'INT'].each do |signal|
    Signal.trap(signal) do
      log('info', "Received signal #{signal}. Initiating graceful shutdown...")

      # Give the server a moment to finish current requests
      Thread.new do
        sleep(2)
        log('info', 'Stopping gRPC server...')
        server.stop
        sleep(1)
        log('info', 'Server stopped. Exiting.')
        exit(0)
      end
    end
  end
end

# Main execution with enhanced error handling
def main
  port = '0.0.0.0:50052'

  begin
    log('info', 'Creating gRPC server...')

    # Create server with enhanced options
    server = GRPC::RpcServer.new(
      pool_size: 15,                    # Increased pool size
      max_waiting_requests: 20,         # Allow more queued requests
      poll_period: 1,                   # Check for shutdown more frequently
      server_args: {
        'grpc.keepalive_time_ms' => 60000,           # 60 seconds
        'grpc.keepalive_timeout_ms' => 20000,        # 20 seconds
        'grpc.keepalive_permit_without_calls' => 1,
        'grpc.http2.max_pings_without_data' => 0,
        'grpc.http2.min_time_between_pings_ms' => 10000,
        'grpc.http2.min_ping_interval_without_data_ms' => 300000
      }
    )

    server.add_http2_port(port, :this_port_is_insecure)
    server.handle(ViaggiatrenoServer.new)

    setup_signal_handlers(server)

    log('info', "🚂 Ruby Viaggiatreno gRPC server started on #{port}")
    log('info', "📡 Server ready to receive requests...")
    log('info', "🔍 Available services:")
    log('info', "   - SearchStations: Search Italian train stations")
    log('info', "   - GetTrainRealtimeStatus: Real-time train status")
    log('info', "📋 Database: SQLite with GTFS data")
    log('info', "🌐 API: Viaggiatreno Fixed for real-time data")
    log('info', "💡 Press Ctrl+C to stop the server")
    log('info', "")

    server.run_till_terminated

  rescue Interrupt
    log('info', 'Interrupt received. Shutting down server...')
  rescue => e
    log('fatal', "Fatal error: #{e.class} - #{e.message}")
    log('fatal', e.backtrace.join("\n")) if e.backtrace
    exit 1
  ensure
    log('info', '🔚 Ruby Viaggiatreno gRPC server terminated.')
  end
end

# Run the server
main if __FILE__ == $0