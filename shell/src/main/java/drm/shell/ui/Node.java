package drm.shell.ui;

import org.jetbrains.skija.Canvas;
import org.jetbrains.skija.Rect;

import java.util.List;

public sealed interface Node permits Scene, Text {
    Rect boundingBox();
    boolean needsRedraw(Canvas canvas);
    Node parent();
    List<Node> children();
    void addChild(Node node);
    void draw(Canvas canvas);
    void damage(Rect rect);
}
