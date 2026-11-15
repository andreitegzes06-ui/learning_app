package com.studymode.ui.view;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public final class ViewComponents {
    private ViewComponents() { }
    public static Label text(String text, String style) {
        Label label = new Label(text); label.setWrapText(true); label.getStyleClass().add(style); return label;
    }
    public static VBox card(Node... children) {
        VBox card = new VBox(12, children); card.getStyleClass().add("card"); return card;
    }
}
