package ssg.legoflow.coap.demo;

import ssg.legoflow.coap.protocol.CoapCode;
import ssg.legoflow.coap.protocol.ContentFormat;
import ssg.legoflow.coap.resource.CoapExchange;
import ssg.legoflow.coap.resource.CoapResource;
import ssg.legoflow.coap.server.CoapServer;
import ssg.legoflow.coap.server.CoapServerConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Demonstrates CoAP observe functionality where a server pushes periodic
 * temperature updates to observing clients.
 *
 * @since 0.1.0
 */
public final class ObserveDemo {

    private final CoapServer server;
    private final ObservableTemperatureResource temperatureResource;
    private ScheduledExecutorService scheduler;

    /**
     * Creates the observe demo with a server on the given port.
     *
     * @param port the UDP port
     * @since 0.1.0
     */
    public ObserveDemo(int port) {
        this.server = new CoapServer(CoapServerConfig.withPort(port));
        this.temperatureResource = new ObservableTemperatureResource();
        server.add(temperatureResource);
    }

    /**
     * Starts the demo server and begins periodic temperature updates.
     *
     * @param intervalMs the update interval in milliseconds
     * @throws IOException if binding fails
     * @since 0.1.0
     */
    public void start(long intervalMs) throws IOException {
        server.start();
        scheduler = Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory());
        scheduler.scheduleAtFixedRate(this::updateTemperature, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Stops the demo server.
     *
     * @since 0.1.0
     */
    public void stop() {
        if (scheduler != null) scheduler.shutdownNow();
        server.stop();
    }

    /**
     * Returns the server.
     *
     * @return the server
     * @since 0.1.0
     */
    public CoapServer server() {
        return server;
    }

    /**
     * Returns the temperature resource.
     *
     * @return the temperature resource
     * @since 0.1.0
     */
    public ObservableTemperatureResource temperatureResource() {
        return temperatureResource;
    }

    /**
     * Manually sets the temperature and notifies observers.
     *
     * @param temperature the new temperature value
     * @since 0.1.0
     */
    public void setTemperature(String temperature) {
        temperatureResource.setValue(temperature);
        temperatureResource.notifyObservers();
    }

    private void updateTemperature() {
        double temp = 20.0 + Math.random() * 10.0;
        temperatureResource.setValue(String.format("%.1f", temp));
        temperatureResource.notifyObservers();
    }

    /**
     * Observable temperature resource that pushes notifications on value changes.
     *
     * @since 0.1.0
     */
    public static final class ObservableTemperatureResource extends CoapResource {

        private final AtomicReference<String> value = new AtomicReference<>("20.0");

        /** Creates the observable temperature resource. */
        public ObservableTemperatureResource() {
            super("temperature", "/sensors/temperature", true);
            getAttributes().resourceType("temperature")
                    .contentFormat(ContentFormat.TEXT_PLAIN.value())
                    .observable(true)
                    .title("Observable Temperature");
        }

        @Override
        public void handleGet(CoapExchange exchange) {
            exchange.respond(CoapCode.CONTENT,
                    value.get().getBytes(StandardCharsets.UTF_8),
                    ContentFormat.TEXT_PLAIN.value());
        }

        /** Sets the current temperature value. */
        public void setValue(String value) {
            this.value.set(value);
        }

        /** Returns the current temperature value. */
        public String currentValue() {
            return value.get();
        }
    }
}
