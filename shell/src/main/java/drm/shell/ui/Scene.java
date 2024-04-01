package drm.shell.ui;

import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.types.Rect;
import org.tinylog.Logger;

import java.util.*;

public final class Scene implements Node {
    private Node parent;
    private final List<Node> children = new ArrayList<>();
    private final Rect boundingBox;
    public final Paint backgroundPaint;

    public Scene(Rect boundingBox, Paint backgroundPaint) {
        this.boundingBox = boundingBox;
        this.backgroundPaint = backgroundPaint;

        this.damagedRegion = boundingBox;
    }

    public void setParent(Node parent) {
        this.parent = parent;
        parent.addChild(this);
    }

    @Override
    public Rect boundingBox() {
        return boundingBox;
    }

    @Override
    public boolean needsRedraw(Canvas canvas) {
        return damagedRegion != null || children.stream().anyMatch(n -> n.needsRedraw(canvas));
    }

    @Override
    public Node parent() {
        return null;
    }

    @Override
    public List<Node> children() {
        return children;
    }

    @Override
    public void addChild(Node node) {
        children.add(node);
    }

    @Override
    public void draw(Canvas canvas) {
        if (redrawHelper.needsRedraw(canvas) && damagedRegion != null) {
            Rect intersect = damagedRegion.intersect(boundingBox);
            Logger.debug("Drawing scene, clearing background at {}", intersect);
            canvas.drawRect(intersect, backgroundPaint);
            redrawHelper.markCanvasUpToDate(canvas);
            if (!redrawHelper.anyNeedsRedraw()) {
                damagedRegion = null;
            }
        }

        children.stream()
                .filter(n -> n.needsRedraw(canvas))
                .forEach(node -> {
                    canvas.drawRect(node.boundingBox(), backgroundPaint);
                    node.forceDraw(canvas);
                });
    }

    @Override
    public void forceDraw(Canvas canvas) {
        canvas.drawRect(boundingBox, backgroundPaint);
        children.forEach(node -> {
                    node.forceDraw(canvas);
                });
    }

    private final RedrawHelper redrawHelper = new RedrawHelper();
    private Rect damagedRegion;

    @Override
    public void damage(Rect rect) {
        redrawHelper.invalidate();
        if (damagedRegion == null) {
            damagedRegion = rect;
        } else {
            damagedRegion = Rect.makeLTRB(Math.min(damagedRegion.getLeft(), rect.getLeft()),
                    Math.min(damagedRegion.getTop(), rect.getTop()),
                    Math.max(damagedRegion.getRight(), rect.getRight()),
                    Math.max(damagedRegion.getBottom(), rect.getBottom()));
        }
        damagedRegion = damagedRegion.inflate(0.1f);

        children.stream()
                .filter(n -> n.boundingBox().intersect(damagedRegion) != null)
                .forEach(n -> n.damage(rect));
    }
}
