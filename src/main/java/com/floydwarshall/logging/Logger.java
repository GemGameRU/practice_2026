package com.floydwarshall.logging;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Logger {
    public enum Type {
        INFO, // информационное сообщение
        ACTION, // действие пользователя
        STATE, // изменение состояния алгоритма
        ERROR // ошибка
    }

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ObservableList<String> entries = FXCollections.observableArrayList();

    public void log(Type type, String message) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String entry = String.format("[%s] [%s] %s", timestamp, type, message);
        entries.add(entry);
    }

    public ObservableList<String> getEntries() {
        return entries;
    }

    public void clear() {
        entries.clear();
    }
}
