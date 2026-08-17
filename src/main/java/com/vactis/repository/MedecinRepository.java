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

// Repository JPA pour l'accès aux données du portefeuille des médecins
@Repository
public interface MedecinRepository extends JpaRepository<Medecin, Long> {

    // Recherche un médecin par son code unique (sensible à la casse)
    Optional<Medecin> findByCodeMedecin(String codeMedecin);

    // Recherche un médecin par son code unique (insensible à la casse)
    Optional<Medecin> findByCodeMedecinIgnoreCase(String codeMedecin);

    // Retourne les médecins par statut de performance
    List<Medecin> findByStatut(StatutMedecin statut);

    // Compte le nombre total de médecins en base
    @Query("""
        select count(m) as nbrMedecins
        from Medecin m
    """)
    Long countAllMedecins();

    // Compte les médecins d'un segment donné
    Long countAllBySegment(String segment);

    // Compte les médecins dont le segment appartient à une liste
    Long countBySegmentIn(Collection<String> segments);

    // Retourne les médecins d'un segment spécifique
    List<Medecin> findBySegment(String segment);

    // Compte les médecins par statut de pilotage commercial
    Long countAllByStatutPilotage(StatutPilotage statutPilotage);

    // Retourne les médecins par statut de pilotage commercial
    List<Medecin> findAllByStatutPilotage(StatutPilotage statutPilotage);

    // Compte le nombre de médecins n'ayant pas encore de note potentielle
    @Query("""
        select count(m)
        from Medecin m
        where m.noteInput is null
    """)
    Long countByNoteInputIsNull();

    // Recherche multi-critères sur les médecins (nom, spécialité, organisme, etc.)
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
          and (:sansNoteInput is null or (:sansNoteInput = true and m.noteInput is null) or (:sansNoteInput = false and m.noteInput is not null))
        order by m.nom, m.prenom
    """)
    List<Medecin> searchMedecins(
            @Param("search") String search,
            @Param("statutPilotage") StatutPilotage statutPilotage,
            @Param("statut") String statut,
            @Param("segment") String segment,
            @Param("specialite") String specialite,
            @Param("risqueUrgence") RisqueUrgence risqueUrgence,
            @Param("organisme") String organisme,
            @Param("sansNoteInput") Boolean sansNoteInput
    );

    // Retourne la liste distincte des spécialités médicales enregistrées
    @Query("""
        select distinct m.specialite
        from Medecin m
        where m.specialite is not null
        order by m.specialite
    """)
    List<String> findDistinctSpecialites();

    // Retourne la liste distincte des organismes/établissements enregistrés
    @Query("""
        select distinct m.organisme
        from Medecin m
        where m.organisme is not null
        order by m.organisme
    """)
    List<String> findDistinctOrganismes();
}
