package pro.sketchware.analysis;

/** How much a finding matters. The weight is what it costs the health score. */
public enum Severity {
    /** Breaks the build or the app, or is a clear security hole. */
    ERROR(15),
    /** Likely to cause trouble on some devices or later; worth fixing. */
    WARNING(5),
    /** Good to know or a small improvement. */
    INFO(1);

    private final int weight;

    Severity(int weight) {
        this.weight = weight;
    }

    public int weight() {
        return weight;
    }
}
