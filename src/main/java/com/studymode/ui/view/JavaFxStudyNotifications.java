package com.studymode.ui.view;

import com.studymode.application.port.StudyNotifications;
import javafx.application.Platform;
import javafx.animation.PauseTransition;
import javafx.scene.control.Label;
import javafx.stage.Popup;
import javafx.stage.Window;
import javafx.util.Duration;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** Portable nonmodal JavaFX fallback; native Windows toast registration is not required. */
public final class JavaFxStudyNotifications implements StudyNotifications, AutoCloseable {
    private final Supplier<Window> owner;
    private final ExecutorService audio = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r,"studymode-completion-sound"); t.setDaemon(true); return t;
    });
    private final Popup popup = new Popup();
    private final PauseTransition expiry = new PauseTransition(Duration.seconds(6));
    private volatile boolean closed;
    public JavaFxStudyNotifications(Supplier<Window> owner) {
        this.owner=owner; expiry.setOnFinished(e -> popup.hide());
    }
    @Override public void publish(Event event, String detail, boolean sound) {
        if(closed) return;
        Platform.runLater(() -> {
            if(closed) return;
            Window window=owner.get(); if(window==null || !window.isShowing()) return;
            Label label=new Label(event.name().replace('_',' ') + "\n" + detail);
            label.setId("study-notification"); label.setWrapText(true); label.setMaxWidth(360);
            label.setStyle("-fx-background-color: #243348; -fx-text-fill: white; -fx-padding: 20; -fx-font-size: 15;");
            popup.getContent().setAll(label); popup.setAutoFix(true); popup.setAutoHide(true);
            popup.show(window,window.getX()+30,window.getY()+65); expiry.playFromStart();
        });
        if(sound) audio.execute(() -> { try { java.awt.Toolkit.getDefaultToolkit().beep(); } catch(RuntimeException ignored) { /* optional audio unavailable */ } });
    }
    @Override public void close() { closed=true; expiry.stop(); popup.hide(); audio.shutdownNow(); }
}
