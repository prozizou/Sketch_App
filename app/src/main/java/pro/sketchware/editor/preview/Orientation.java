package pro.sketchware.editor.preview;

public enum Orientation {
    PORTRAIT, LANDSCAPE;

    public Orientation flip() {
        return this == PORTRAIT ? LANDSCAPE : PORTRAIT;
    }
}
