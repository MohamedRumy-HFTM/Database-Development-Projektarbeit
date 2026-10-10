package ch.hftm.seelandmobil.kunde;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KundeRepository extends JpaRepository<Kunde, Long> {

    /** Suche in Name und E-Mail, unabhängig von Gross-/Kleinschreibung. "!" ist das Escape-Zeichen. */
    @Query(value = """
            select k from Kunde k
            where lower(k.name) like :muster escape '!' or lower(k.email) like :muster escape '!'
            order by k.name, k.id
            """,
            countQuery = """
            select count(k) from Kunde k
            where lower(k.name) like :muster escape '!' or lower(k.email) like :muster escape '!'
            """)
    Page<Kunde> suche(@Param("muster") String muster, Pageable pageable);
}
