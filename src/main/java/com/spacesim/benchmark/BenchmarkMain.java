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
     * <p>На первом Stage-6 vertical slice поддерживается только стабильный scenario {@code smoke}.
     * Большой 100/500/100h scenario будет добавлен поверх того же runner после масштабируемой world
     * factory, а не через изменение семантики smoke-case.</p>
     *
     * @param args пустой список либо единственный аргумент {@code smoke}
     */
    public static void main(String[] args) {
        if (args == null || args.length > 1 || (args.length == 1 && !"smoke".equals(args[0]))) {
            throw new IllegalArgumentException("Использование: BenchmarkMain [smoke]");
        }
        BenchmarkReport report = new EconomicBenchmarkRunner().run(BenchmarkScenario.smoke());
        System.out.println(report.toJson());
    }
}
