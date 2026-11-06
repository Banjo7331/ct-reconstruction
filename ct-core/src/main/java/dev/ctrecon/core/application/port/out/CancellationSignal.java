package dev.ctrecon.core.application.port.out;

@FunctionalInterface
public interface CancellationSignal {

    boolean isCancellationRequested();
}
