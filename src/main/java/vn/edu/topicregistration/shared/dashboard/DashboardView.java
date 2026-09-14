package vn.edu.topicregistration.shared.dashboard;

import java.util.List;

/** Only presentation-ready, actor-scoped data may be passed to the dashboard. */
public record DashboardView(
        String audience,
        String title,
        String subtitle,
        List<Metric> metrics,
        Focus focus) {
    public DashboardView {
        metrics = List.copyOf(metrics);
    }

    public record Metric(String label, long value, String description, String href) {}

    public record Focus(String label, String title, String description,
                        String href, String actionLabel, String badgeClass) {}
}
