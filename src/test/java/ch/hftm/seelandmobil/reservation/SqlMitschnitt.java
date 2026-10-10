package ch.hftm.seelandmobil.reservation;

import org.hibernate.resource.jdbc.spi.StatementInspector;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Merkt sich jedes SQL, das Hibernate an die Datenbank schickt (nur in Tests eingebunden). */
public class SqlMitschnitt implements StatementInspector {

    private static final List<String> SQL = new CopyOnWriteArrayList<>();

    @Override
    public String inspect(String sql) {
        SQL.add(sql);
        return sql;
    }

    static void leeren() {
        SQL.clear();
    }

    static List<String> alle() {
        return List.copyOf(SQL);
    }
}
