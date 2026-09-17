package com.portfolio.chungyak.web.view;

/** 캘린더 한 칸에 얹는 공고 일정 한 건 — 접수 시작 또는 마감. */
public record CalendarEvent(Long announcementId, String houseName, Kind kind) {

    public enum Kind { START, END }
}
