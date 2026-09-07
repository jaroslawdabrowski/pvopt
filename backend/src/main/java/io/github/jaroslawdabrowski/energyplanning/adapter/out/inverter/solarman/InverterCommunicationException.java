package io.github.jaroslawdabrowski.energyplanning.adapter.out.inverter.solarman;

class InverterCommunicationException extends RuntimeException {

    InverterCommunicationException(String message) {
        super(message);
    }

    InverterCommunicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
