package io.github.riktigrefactor.java;

public record Warning(String code, String message, String file, int start, int end) {}
