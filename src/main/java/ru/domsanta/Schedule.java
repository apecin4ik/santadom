package ru.domsanta;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Расчёт ближайшего времени запуска по списку «ЧЧ:мм» в заданном часовом поясе. */
public final class Schedule {

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("H:mm");

    private Schedule() {}

    /** Часовой пояс по id; если не распознан - Москва (UTC+3, без перехода на летнее время). */
    public static ZoneId zone(String id) {
        try {
            return ZoneId.of(id == null || id.isBlank() ? "Europe/Moscow" : id.trim());
        } catch (Exception ex) {
            return ZoneOffset.ofHours(3);
        }
    }

    /** @return время или null, если строка некорректна */
    public static LocalTime parse(String s) {
        if (s == null) return null;
        try {
            return LocalTime.parse(s.trim(), HM);
        } catch (Exception ex) {
            return null;
        }
    }

    /** @return ближайший момент (мс) строго после now, либо -1 если нет ни одного корректного времени */
    public static long nextMillis(List<String> times, ZoneId zone, long nowMillis) {
        ZonedDateTime now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zone);
        long best = -1;
        for (String t : times) {
            LocalTime lt = parse(t);
            if (lt == null) continue;
            ZonedDateTime cand = now.with(lt);
            if (!cand.isAfter(now)) cand = cand.plusDays(1);
            long ms = cand.toInstant().toEpochMilli();
            if (best < 0 || ms < best) best = ms;
        }
        return best;
    }
}
