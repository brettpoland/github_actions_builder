package com.example.githubactionsbuilder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/**
 * Simple JavaFX wizard for creating GitHub Actions YAML files.
 */
public class GitHubActionsBuilderApp extends Application {

    private Stage stage;
    private int runnerCount;
    private final List<RunnerConfig> configs = new ArrayList<>();
    private int currentRunner = 0;

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        stage.setTitle("GitHub Actions Workflow Builder");
        showRunnerCountScene();
    }

    private void showRunnerCountScene() {
        Label label = new Label("Select number of runners:");
        Spinner<Integer> spinner = new Spinner<>(1, 10, 1);
        Button next = new Button("Next");
        next.setOnAction(e -> {
            runnerCount = spinner.getValue();
            configs.clear();
            currentRunner = 0;
            showRunnerConfigScene();
        });
        VBox root = new VBox(10, label, spinner, next);
        root.setPadding(new Insets(20));
        stage.setScene(new Scene(root));
        stage.show();
    }

    private void showRunnerConfigScene() {
        RunnerConfigForm form = new RunnerConfigForm(currentRunner + 1, runnerCount);
        form.getNextButton().setOnAction(e -> {
            configs.add(form.toConfig());
            currentRunner++;
            if (currentRunner < runnerCount) {
                showRunnerConfigScene();
            } else {
                showSummaryScene();
            }
        });
        stage.setScene(new Scene(form, 400, 300));
    }

    private void showSummaryScene() {
        Button save = new Button("Save YAML");
        Button quit = new Button("Quit");
        save.setOnAction(e -> saveYaml());
        quit.setOnAction(e -> Platform.exit());
        VBox root = new VBox(10, new Label("Configuration complete."), save, quit);
        root.setPadding(new Insets(20));
        stage.setScene(new Scene(root));
    }

    private void saveYaml() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save GitHub Actions Workflow");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("YAML Files", "*.yml", "*.yaml"));
        File file = chooser.showSaveDialog(stage);
        if (file != null) {
            String yaml = buildYaml();
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(yaml);
            } catch (IOException ex) {
                new Alert(Alert.AlertType.ERROR, "Failed to save file: " + ex.getMessage()).showAndWait();
            }
        }
    }

    private String buildYaml() {
        Set<String> triggers = new LinkedHashSet<>();
        for (RunnerConfig cfg : configs) {
            triggers.addAll(cfg.triggers);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("name: Generated Workflow\n");
        sb.append("on:\n");
        for (String t : triggers) {
            sb.append("  ").append(t).append(":\n");
        }
        sb.append("jobs:\n");
        for (RunnerConfig cfg : configs) {
            sb.append("  ").append(cfg.name).append(":\n");
            sb.append("    runs-on: ubuntu-latest\n");
            if (!cfg.triggers.isEmpty()) {
                sb.append("    if: ");
                for (int i = 0; i < cfg.triggers.size(); i++) {
                    if (i > 0) sb.append(" || ");
                    sb.append("github.event_name == '").append(cfg.triggers.get(i)).append("'");
                }
                sb.append("\n");
            }
            sb.append("    steps:\n");
            sb.append("      - uses: actions/checkout@v3\n");
        }
        return sb.toString();
    }

    public static void main(String[] args) {
        launch(args);
    }

    private static class RunnerConfig {
        final String name;
        final List<String> triggers;

        RunnerConfig(String name, List<String> triggers) {
            this.name = name;
            this.triggers = triggers;
        }
    }

    private static class RunnerConfigForm extends VBox {
        private final TextField nameField = new TextField();
        private final CheckBox pushBox = new CheckBox("push");
        private final CheckBox prBox = new CheckBox("pull_request");
        private final CheckBox dispatchBox = new CheckBox("workflow_dispatch");
        private final Button next = new Button("Next");

        RunnerConfigForm(int index, int total) {
            setSpacing(10);
            setPadding(new Insets(20));
            getChildren().addAll(
                    new Label("Runner " + index + " of " + total),
                    new Label("Job name:"), nameField,
                    new Label("Triggers:"),
                    pushBox, prBox, dispatchBox,
                    next
            );
            nameField.setText("runner" + index);
            if (index == total) {
                next.setText("Finish");
            }
        }

        Button getNextButton() {
            return next;
        }

        RunnerConfig toConfig() {
            List<String> triggers = new ArrayList<>();
            if (pushBox.isSelected()) triggers.add("push");
            if (prBox.isSelected()) triggers.add("pull_request");
            if (dispatchBox.isSelected()) triggers.add("workflow_dispatch");
            return new RunnerConfig(nameField.getText().trim(), triggers);
        }
    }
}
