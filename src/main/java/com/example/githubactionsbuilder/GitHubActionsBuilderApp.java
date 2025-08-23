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
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.collections.FXCollections;
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
    private final List<String> workflowTriggers = new ArrayList<>();

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        stage.setTitle("GitHub Actions Workflow Builder");
        showRunnerCountScene();
    }

    private void showRunnerCountScene() {
        Label label = new Label("Select number of runners:");
        Spinner<Integer> spinner = new Spinner<>(1, 10, 1);
        Label eventLabel = new Label("Select workflow triggers:");
        CheckBox push = new CheckBox("push");
        CheckBox pr = new CheckBox("pull_request");
        CheckBox dispatch = new CheckBox("workflow_dispatch");
        CheckBox schedule = new CheckBox("schedule");
        CheckBox issues = new CheckBox("issues");
        CheckBox release = new CheckBox("release");
        Button next = new Button("Next");
        next.setOnAction(e -> {
            runnerCount = spinner.getValue();
            workflowTriggers.clear();
            if (push.isSelected()) workflowTriggers.add("push");
            if (pr.isSelected()) workflowTriggers.add("pull_request");
            if (dispatch.isSelected()) workflowTriggers.add("workflow_dispatch");
            if (schedule.isSelected()) workflowTriggers.add("schedule");
            if (issues.isSelected()) workflowTriggers.add("issues");
            if (release.isSelected()) workflowTriggers.add("release");
            configs.clear();
            currentRunner = 0;
            showRunnerConfigScene();
        });
        VBox root = new VBox(10, label, spinner, eventLabel, push, pr, dispatch, schedule, issues, release, next);
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
        Set<String> triggers = new LinkedHashSet<>(workflowTriggers);
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
            sb.append("    runs-on: ").append(cfg.runsOn).append("\n");
            if (!cfg.cloud.isEmpty() || !cfg.accessKeySecret.isEmpty() || !cfg.secretKeySecret.isEmpty()) {
                sb.append("    env:\n");
                if (!cfg.cloud.isEmpty()) {
                    sb.append("      CLOUD_PROVIDER: ").append(cfg.cloud).append("\n");
                }
                if (!cfg.accessKeySecret.isEmpty()) {
                    sb.append("      ACCESS_KEY: ${{ secrets.").append(cfg.accessKeySecret).append(" }}\n");
                }
                if (!cfg.secretKeySecret.isEmpty()) {
                    sb.append("      SECRET_KEY: ${{ secrets.").append(cfg.secretKeySecret).append(" }}\n");
                }
            }
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
        final String runsOn;
        final String cloud;
        final String accessKeySecret;
        final String secretKeySecret;

        RunnerConfig(String name, List<String> triggers, String runsOn, String cloud,
                     String accessKeySecret, String secretKeySecret) {
            this.name = name;
            this.triggers = triggers;
            this.runsOn = runsOn;
            this.cloud = cloud;
            this.accessKeySecret = accessKeySecret;
            this.secretKeySecret = secretKeySecret;
        }
    }

    private static class RunnerConfigForm extends VBox {
        private final TextField nameField = new TextField();
        private final ComboBox<String> runsOnBox = new ComboBox<>(
                FXCollections.observableArrayList("ubuntu-latest", "windows-latest", "macos-latest"));
        private final ComboBox<String> cloudBox = new ComboBox<>(
                FXCollections.observableArrayList("AWS", "Azure", "GCP"));
        private final TextField accessKeyField = new TextField();
        private final TextField secretKeyField = new TextField();
        private final CheckBox pushBox = new CheckBox("push");
        private final CheckBox prBox = new CheckBox("pull_request");
        private final CheckBox dispatchBox = new CheckBox("workflow_dispatch");
        private final CheckBox scheduleBox = new CheckBox("schedule");
        private final CheckBox issuesBox = new CheckBox("issues");
        private final CheckBox releaseBox = new CheckBox("release");
        private final Button next = new Button("Next");

        RunnerConfigForm(int index, int total) {
            setSpacing(10);
            setPadding(new Insets(20));
            runsOnBox.setValue("ubuntu-latest");
            cloudBox.setValue("AWS");
            getChildren().addAll(
                    new Label("Runner " + index + " of " + total),
                    new Label("Job name:"), nameField,
                    new Label("Runs on:"), runsOnBox,
                    new Label("Cloud provider:"), cloudBox,
                    new Label("Access key secret name:"), accessKeyField,
                    new Label("Secret key secret name:"), secretKeyField,
                    new Label("Triggers:"),
                    pushBox, prBox, dispatchBox, scheduleBox, issuesBox, releaseBox,
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
            if (scheduleBox.isSelected()) triggers.add("schedule");
            if (issuesBox.isSelected()) triggers.add("issues");
            if (releaseBox.isSelected()) triggers.add("release");
            return new RunnerConfig(
                    nameField.getText().trim(),
                    triggers,
                    runsOnBox.getValue(),
                    cloudBox.getValue(),
                    accessKeyField.getText().trim(),
                    secretKeyField.getText().trim());
        }
    }
}
