package com.flowstate.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * User configuration and classification preference for a specific application.
 */
public class ApplicationPreference implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String applicationIdentifier;
    private String displayName;
    private AppCategory category;
    private boolean isExcluded;

    public ApplicationPreference() {
        this.category = AppCategory.NEUTRAL;
        this.isExcluded = false;
    }

    public ApplicationPreference(String applicationIdentifier, String displayName,
                                 AppCategory category, boolean isExcluded) {
        this.applicationIdentifier = applicationIdentifier != null ? applicationIdentifier.trim().toLowerCase() : "";
        this.displayName = displayName != null ? displayName.trim() : "";
        this.category = category != null ? category : AppCategory.NEUTRAL;
        this.isExcluded = isExcluded;
    }

    public ApplicationPreference(Long id, String applicationIdentifier, String displayName,
                                 AppCategory category, boolean isExcluded) {
        this.id = id;
        this.applicationIdentifier = applicationIdentifier;
        this.displayName = displayName;
        this.category = category != null ? category : AppCategory.NEUTRAL;
        this.isExcluded = isExcluded;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getApplicationIdentifier() {
        return applicationIdentifier;
    }

    public void setApplicationIdentifier(String applicationIdentifier) {
        this.applicationIdentifier = applicationIdentifier != null ? applicationIdentifier.trim().toLowerCase() : null;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public AppCategory getCategory() {
        return category;
    }

    public void setCategory(AppCategory category) {
        this.category = category;
    }

    public boolean isExcluded() {
        return isExcluded;
    }

    public void setExcluded(boolean excluded) {
        isExcluded = excluded;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ApplicationPreference that)) return false;
        return Objects.equals(applicationIdentifier, that.applicationIdentifier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(applicationIdentifier);
    }

    @Override
    public String toString() {
        return "ApplicationPreference{" +
                "identifier='" + applicationIdentifier + '\'' +
                ", displayName='" + displayName + '\'' +
                ", category=" + category +
                ", isExcluded=" + isExcluded +
                '}';
    }
}
