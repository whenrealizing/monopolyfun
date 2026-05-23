package com.monopolyfun.modules.project.service.view;

public record DashboardSectionView<T>(
        String status,
        T data,
        String errorCode
) {
    public static <T> DashboardSectionView<T> visible(T data) {
        return new DashboardSectionView<>("visible", data, null);
    }

    public static <T> DashboardSectionView<T> unavailable(String status, String errorCode, T fallback) {
        return new DashboardSectionView<>(status, fallback, errorCode);
    }
}
