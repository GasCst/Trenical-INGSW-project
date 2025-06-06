#!/usr/bin/env ruby

this_dir = File.expand_path(File.dirname(__FILE__))
lib_dir = File.join(this_dir, 'lib')
$LOAD_PATH.unshift(lib_dir) unless $LOAD_PATH.include?(lib_dir)

require 'grpc'
require_relative 'viaggiatreno_service_pb'
require_relative 'viaggiatreno_service_services_pb'
require 'open-uri'
require 'net/http'
require 'nokogiri'

# --- Start of Kernel.open Monkey Patch ---
# This patch intercepts HTTP calls from the gem and uses Net::HTTP,
# which works reliably in your environment.
module Kernel
  alias_method :original_kernel_open_for_patch, :open

  def open(name, *rest, &block)
    if name.is_a?(String) && (name.start_with?('http://') || name.start_with?('https://'))
      uri = URI.parse(name)
      response = nil
      current_uri = uri
      max_redirects = 5

      max_redirects.times do
        http_obj = Net::HTTP.new(current_uri.host, current_uri.port)
        http_obj.use_ssl = (current_uri.scheme == 'https')
        http_obj.open_timeout = 10
        http_obj.read_timeout = 10

        req = Net::HTTP::Get.new(current_uri.request_uri)
        req['User-Agent'] = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/99.0.4844.51 Safari/537.36'

        current_response = http_obj.start { |http_session| http_session.request(req) }

        if current_response.is_a?(Net::HTTPRedirection) && current_response['location']
          new_uri_str = current_response['location']
          new_uri = URI.parse(new_uri_str)
          current_uri = current_uri.merge(new_uri)
          response = nil
        else
          response = current_response
          break
        end
      end

      raise "Failed to fetch URL after redirects: #{name}" if response.nil?
      response.value # Raise error for non-2xx responses

      string_io_response = StringIO.new(response.body)
      def string_io_response.status; [self.instance_variable_get(:@response_code) || "200", self.instance_variable_get(:@response_message) || "OK"]; end
      string_io_response.instance_variable_set(:@response_code, response.code)
      string_io_response.instance_variable_set(:@response_message, response.message)

      if block_given?
        begin
          yield string_io_response
        ensure
          string_io_response.close
        end
      else
        return string_io_response
      end
    else
      original_kernel_open_for_patch(name, *rest, &block)
    end
  rescue StandardError => e
    # Re-raise the error to be caught by the application's main error handler
    raise
  end
end
# --- End of Kernel.open Monkey Patch ---

begin
  require 'viaggiatreno'
rescue LoadError => e
  puts "FATAL: Failed to load 'viaggiatreno' gem. Please ensure it is installed."
  puts e.message
  exit 1
end

class ViaggiatrenoServer < Trenical::RubyViaggiatreno::ViaggiatrenoService::Service
  def get_train_realtime_status(request, _call)
    train_number_str = request.train_number.to_s
    puts "Received GetTrainRealtimeStatus request for train: #{train_number_str}"

    response = Trenical::RubyViaggiatreno::TrainStatusResponse.new(
      found: false,
      train_number: train_number_str
    )

    begin
      vt_train = Train.new(train_number_str)

      train_found = false
      if vt_train.status && !vt_train.status.to_s.strip.empty?
        normalized_status = vt_train.status.to_s.downcase
        is_error_status = ['non trovato', 'not found', 'cancellato', 'cancelled', 'soppresso', 'suppressed'].any? do |keyword|
          normalized_status.include?(keyword)
        end

        if !is_error_status && vt_train.train_name && !vt_train.train_name.to_s.strip.empty?
          train_found = true
        end
      end

      if train_found
        response.found = true

        response.train_category = vt_train.train_name.to_s.split.first || ""
        response.origin_station = first_available_method_to_s(vt_train, [:origin_station, :departing_station])
        response.destination_station = first_available_method_to_s(vt_train, [:destination_station, :arriving_station])
        response.scheduled_departure_time = first_available_method_to_s(vt_train, [:scheduled_departure_time])
        response.scheduled_arrival_time = first_available_method_to_s(vt_train, [:scheduled_arrival_time])
        response.actual_departure_time = first_available_method_to_s(vt_train, [:actual_departure_time])
        response.actual_arrival_time = first_available_method_to_s(vt_train, [:actual_arrival_time])
        response.delay_minutes = vt_train.delay.to_i if vt_train.respond_to?(:delay)
        response.train_status_description = vt_train.status.to_s

        if vt_train.respond_to?(:last_update) && vt_train.last_update
          response.last_detected_station = vt_train.last_update.to_s
          response.last_detection_time = ""
        else
          response.last_detected_station = ""
          response.last_detection_time = ""
        end

      else
        if vt_train.status && !vt_train.status.to_s.strip.empty?
          response.error_message = "Train #{train_number_str} status: #{vt_train.status}"
        else
          response.error_message = "Train #{train_number_str} not found or error fetching details."
        end
      end

    rescue NoMethodError => e
      puts "ERROR: Method missing with Viaggiatreno library for train #{train_number_str}: #{e.message}"
      puts e.backtrace.first(5).join("\n")
      response.error_message = "Internal server error: Incompatible Viaggiatreno library API. Method '#{e.name}' missing."
      response.found = false
    rescue StandardError => e
      puts "ERROR: Processing request for train #{train_number_str}: #{e.class} - #{e.message}"
      puts e.backtrace.join("\n")
      response.error_message = "Internal server error while fetching train data: #{e.message}"
      response.found = false
    end

    puts "Sending response for train #{train_number_str}: Found=#{response.found}, ErrorMsg='#{response.error_message}'"
    response
  end

  private

  def first_available_method_to_s(object, methods_to_try)
    methods_to_try.each do |method_sym|
      if object.respond_to?(method_sym)
        value = object.public_send(method_sym)
        return value.to_s unless value.nil?
      end
    end
    ""
  end
end

# Main
def main
  port = '0.0.0.0:50052'
  server = GRPC::RpcServer.new
  server.add_http2_port(port, :this_port_is_insecure)
  server.handle(ViaggiatrenoServer.new)
  puts "Ruby Viaggiatreno gRPC server running on #{port}..."

  begin
    server.run
  rescue Interrupt
    puts "\nRuby Viaggiatreno gRPC server shutting down..."
    server.stop
    puts "Server stopped."
  end
  puts 'Ruby Viaggiatreno gRPC server stopped.'
end

main