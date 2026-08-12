package com.spacesim.benchmark;

/**
 * CLI entry point headless economic benchmark без создания libGDX/OpenGL application.
 */
public final class BenchmarkMain {
    private BenchmarkMain() {
        throw new AssertionError("BenchmarkMain не создаёт экземпляров");
    }

    /**
     * Запускает versioned benchmark scenario и печатает один JSON report в stdout.
     *
     * <p>{@code smoke} использует production demo world. {@code scale100h} использует
     * {@link BenchmarkWorldFactory} с 100 stations / 500 economic agents и выполняет ровно 100
     * simulated hours. Тяжёлый scenario намеренно не является default.</p>
     *
     * @param args пустой список, {@code smoke} либо {@code scale100h}
     */
    public static void main(String[] args) {
        if (args == null || args.length > 1) {
            throw usage();
        }
        String mode = args.length == 0 ? "smoke" : args[0];
        BenchmarkReport report = switch (mode) {
            case "smoke" -> new EconomicBenchmarkRunner().run(BenchmarkScenario.smoke());
            case "scale100h" -> new EconomicBenchmarkRunner(BenchmarkWorldFactory::createScale100x500)
                    .run(BenchmarkScenario.scale100Hours());
            default -> throw usage();
        };
        System.out.println(report.toJson());
    }

    private static IllegalArgumentException usage() {
        return new IllegalArgumentException("Использование: BenchmarkMain [smoke|scale100h]");
    }
}
