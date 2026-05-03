package com.weaversworkshop.playerdisguise.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record ProfileBook(List<Profile> profiles, int activeIndex) {
    public static final ProfileBook EMPTY = new ProfileBook(List.of(), 0);

    public ProfileBook {
        profiles = profiles == null ? List.of() : List.copyOf(profiles);
        int max = profiles.size();
        if (activeIndex < 0 || activeIndex > max) activeIndex = 0;
    }

    public boolean isRealActive() {
        return activeIndex == 0;
    }

    public Profile activeStored() {
        return activeIndex == 0 ? null : profiles.get(activeIndex - 1);
    }

    public ProfileBook withActiveIndex(int idx) {
        return new ProfileBook(profiles, idx);
    }

    public ProfileBook addProfile(Profile p) {
        List<Profile> next = new ArrayList<>(profiles);
        next.add(p);
        return new ProfileBook(next, next.size());
    }

    public ProfileBook replaceProfile(int storedIndex, Profile p) {
        List<Profile> next = new ArrayList<>(profiles);
        next.set(storedIndex, p);
        return new ProfileBook(next, activeIndex);
    }

    public ProfileBook removeProfile(int storedIndex) {
        List<Profile> next = new ArrayList<>(profiles);
        next.remove(storedIndex);
        int newActive = activeIndex;
        if (activeIndex == storedIndex + 1) newActive = 0;
        else if (activeIndex > storedIndex + 1) newActive = activeIndex - 1;
        return new ProfileBook(next, newActive);
    }

    public int totalSlots() {
        return profiles.size() + 1;
    }
}
