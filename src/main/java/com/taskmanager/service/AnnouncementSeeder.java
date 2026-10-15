package com.taskmanager.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class AnnouncementSeeder implements CommandLineRunner {
    private final AnnouncementService announcements;
    @Override public void run(String... args) { announcements.seedWelcome(); }
}
