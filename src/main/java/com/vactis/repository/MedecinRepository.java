package com.vactis.repository;

import com.vactis.model.medecin.Medecin;
import com.vactis.model.medecin.RisqueUrgence;
import com.vactis.model.medecin.StatutMedecin;
import com.vactis.model.medecin.StatutPilotage;








import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;

import org.springframework.data.repository.query.Param;

import org.springframework.stereotype.Repository;



import java.util.Collection;

import java.util.List;

import java.util.Optional;



@Repository

public interface MedecinRepository extends JpaRepository<Medecin, Long> {

    //Retrouve un medecin par son code

    Optional<Medecin> findByCodeMedecin(String codeMedecin);

    Optional<Medecin> findByCodeMedecinIgnoreCase(String codeMedecin);



    //Retrouve les medecins par statut

    List<Medecin> findByStatut(StatutMedecin statut);



    //le nombre des medecins

    @Query("""
        select count(m) as nbrMedecins
        from Medecin m
    """)
    Long countAllMedecins();



    //le nombre des medecins par segment

    Long countAllBySegment(String segment);



    //le nombre des medecins par segments

    Long countBySegmentIn(Collection<String> segments);



    //Retrouve les medecins par segment

    List<Medecin> findBySegment(String segment);



    //le nombre des medecins par statu pilotage

    Long countAllByStatutPilotage(StatutPilotage statutPilotage);



    //Retrouve les medecins par statu pilotage

    List<Medecin> findAllByStatutPilotage(StatutPilotage statutPilotage);



    //Recherche les medecins avec filtres

    @Query("""

        select m

        from Medecin m

        where (:search is null or :search = '' or

               lower(concat(m.nom, ' ', m.prenom)) like lower(concat('%', :search, '%')) or

               lower(m.codeMedecin) like lower(concat('%', :search, '%')) or

               lower(m.specialite) like lower(concat('%', :search, '%')) or

               lower(m.organisme) like lower(concat('%', :search, '%')) or

               lower(m.ville) like lower(concat('%', :search, '%')))

          and (:statutPilotage is null or m.statutPilotage = :statutPilotage)

          and (:statut is null or :statut = '' or m.statut = :statut)

          and (:segment is null or :segment = '' or m.segment = :segment)

          and (:specialite is null or :specialite = '' or m.specialite = :specialite)

          and (:risqueUrgence is null or m.risqueUrgence = :risqueUrgence)

          and (:organisme is null or :organisme = '' or m.organisme = :organisme)

        order by m.nom, m.prenom

    """)

    List<Medecin> searchMedecins(

            @Param("search") String search,

            @Param("statutPilotage") StatutPilotage statutPilotage,

            @Param("statut") String statut,

            @Param("segment") String segment,

            @Param("specialite") String specialite,

            @Param("risqueUrgence") RisqueUrgence risqueUrgence,

            @Param("organisme") String organisme

    );



    //Recupere les specialites distinctes

    @Query("""

        select distinct m.specialite

        from Medecin m

        where m.specialite is not null

        order by m.specialite

    """)

    List<String> findDistinctSpecialites();



    //Recupere les organismes distincts

    @Query("""

        select distinct m.organisme

        from Medecin m

        where m.organisme is not null

        order by m.organisme

    """)

    List<String> findDistinctOrganismes();

}


