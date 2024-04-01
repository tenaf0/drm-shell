package drm.shell.ui;

import io.github.humbleui.skija.Canvas;
import io.github.humbleui.types.Rect;

import java.util.List;

public sealed interface Node permits Scene, Text {
    Rect boundingBox();
    boolean needsRedraw(Canvas canvas);
    Node parent();
    List<Node> children();
    void addChild(Node node);
    void draw(Canvas canvas);
    void forceDraw(Canvas canvas);
    void damage(Rect rect);
}
