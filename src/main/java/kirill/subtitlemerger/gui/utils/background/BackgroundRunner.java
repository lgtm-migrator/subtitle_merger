package kirill.subtitlemerger.gui.utils.background;

@FunctionalInterface
public interface BackgroundRunner<T> {
    T run(BackgroundManager backgroundManager);
}
