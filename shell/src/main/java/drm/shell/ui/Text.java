package drm.shell.ui;

import io.github.humbleui.skija.*;
import io.github.humbleui.skija.paragraph.*;
import io.github.humbleui.types.Rect;
import org.tinylog.Logger;

import java.util.List;

public final class Text implements Node {
    private final Node parent;
    private String text;
    private Rect boundingBox;
    private Paint paint;
    private final float width;
    private Paragraph paragraph;

    private final RedrawHelper redrawHelper = new RedrawHelper();

    private static final Font font = new Font();
    static {
        font.setTypeface(Typeface.makeFromName("Cantarell", FontStyle.NORMAL));
        font.setSize(20.0f);
    }

    public Text(Node parent, String text, Paint paint, Position position, float width) {
        this.parent = parent;
        parent.addChild(this);
        this.text = text;
        this.paint = paint;
        this.width = width;

        this.boundingBox = Rect.makeXYWH(position.x(), position.y(), 0f, 0f);
        setText(text);
        Logger.info("Created text node with box {}", boundingBox);
    }

    public void setText(String text) {
        this.text = text;
        redrawHelper.invalidate();

        ParagraphStyle style = new ParagraphStyle();
        TextStyle textStyle = new TextStyle();
        textStyle.setTypeface(font.getTypeface());
        textStyle.setFontSize(20.0f);
        textStyle.setColor(paint.getColor());
        style.setTextStyle(textStyle);
        FontCollection fc = new FontCollection();
        fc.setDefaultFontManager(FontMgr.getDefault());
        ParagraphBuilder paragraphBuilder = new ParagraphBuilder(style, fc);
        paragraphBuilder.addText(text);
        paragraph = paragraphBuilder.build();
        paragraph.layout(width);

        this.boundingBox = Rect.makeXYWH(boundingBox.getLeft(), boundingBox.getTop(), paragraph.getMaxWidth(), paragraph.getHeight());
    }

    @Override
    public Rect boundingBox() {
        return boundingBox;
    }

    @Override
    public boolean needsRedraw(Canvas canvas) {
        return redrawHelper.needsRedraw(canvas);
    }

    @Override
    public Node parent() {
        return null;
    }

    @Override
    public List<Node> children() {
        return List.of();
    }

    @Override
    public void addChild(Node node) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void draw(Canvas canvas) {
        if (!needsRedraw(canvas)) {
            Logger.debug("Text component up-to-date, no drawing necessary");
            return;
        }

        forceDraw(canvas);
    }

    @Override
    public void forceDraw(Canvas canvas) {
        Logger.debug("Drawing text component {}", text);
        paragraph.paint(canvas, boundingBox.getLeft(), boundingBox.getTop());
        redrawHelper.markCanvasUpToDate(canvas);
    }

    @Override
    public void damage(Rect rect) {
        Logger.debug("Damaging text node");
        redrawHelper.invalidate();
    }
}
