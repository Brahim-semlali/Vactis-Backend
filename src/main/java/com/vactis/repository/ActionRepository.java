package com.vactis.repository;

import com.vactis.model.action.Action;
import com.vactis.model.action.EtatAction;
import com.vactis.model.action.StatutPlanRetention;
import com.vactis.model.action.TypeEtapeRetention;
import com.vactis.model.action.UrgenceAction;
import com.vactis.model.data.ExtractionDonnees;
import com.vactis.model.medecin.StatutPilotage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

// Repository JPA pour l'accès aux données des actions de pilotage commercial
@Repository
public interface ActionRepository extends JpaRepository<Action, Long> {

    // Compte le nombre total d'actions en base
    @Query("""
        select count(a)
        from Action a
    """)
    Long countAllActions();

    Long countByCycleMensuel(String cycleMensuel);

    @Query("""
            select count(a)
            from Action a
            where a.cycleMensuel = :cycleMensuel
                and lower(a.statut) = 'exclu'
    """)
    Long countActionsExcluesDirectionByCycle(@Param("cycleMensuel") String cycleMensuel);

    // Compte les actions par état (PLANIFIEE, REALISEE, etc.)
    Long countByEtatAction(EtatAction etatAction);

    // Compte les actions marquées comme backlog
    Long countByBacklogTrue();

    // Compte les actions en urgence silence critique
    Long countByUrgenceSilenceTrue();

    // Recherche multi-critères sur les actions avec jointure sur le médecin
    @Query("""
        select a
        from Action a
        join fetch a.medecin m
        where exists (
            select e.id from ExtractionDonnees e where e.medecin = m
        )
        and (:search is null or :search = '' or
               lower(concat(m.nom, ' ', m.prenom)) like lower(concat('%', :search, '%')) or
               lower(m.specialite) like lower(concat('%', :search, '%')) or
               lower(a.actionRecommandee) like lower(concat('%', :search, '%')) or
               lower(a.commercial) like lower(concat('%', :search, '%')) or
               lower(a.lieuOrganisme) like lower(concat('%', :search, '%')))
          and (:statut is null or :statut = '' or lower(a.statut) = lower(:statut) or lower(m.statut) = lower(:statut))
          and (:segment is null or :segment = '' or lower(a.segment) = lower(:segment) or lower(m.segment) = lower(:segment))
          and (:action is null or :action = '' or a.actionRecommandee = :action)
          and (:urgence is null or a.urgence = :urgence)
          and (:etatAction is null or a.etatAction = :etatAction)
          and (:backlog is null or a.backlog = :backlog)
          and (:commercial is null or :commercial = '' or a.commercial = :commercial)
          and (:lieuOrganisme is null or :lieuOrganisme = '' or a.lieuOrganisme = :lieuOrganisme)
        order by m.scoreUrgence desc nulls last,
                 m.scoreRisque desc nulls last,
                 case when (m.referenceCa - m.caMois) > 0 then (m.referenceCa - m.caMois) else 0.0 end desc,
                 m.nom, m.prenom
    """)
    List<Action> searchActions(
            @Param("search") String search,
            @Param("statut") String statut,
            @Param("segment") String segment,
            @Param("action") String action,
            @Param("urgence") UrgenceAction urgence,
            @Param("etatAction") EtatAction etatAction,
            @Param("backlog") Boolean backlog,
            @Param("commercial") String commercial,
            @Param("lieuOrganisme") String lieuOrganisme
    );

    // Retourne la liste distincte des intitulés d'actions recommandées
    @Query("""
        select distinct a.actionRecommandee
        from Action a
        where a.actionRecommandee is not null
        order by a.actionRecommandee
    """)
    List<String> findDistinctActions();

    // Retourne la liste distincte des commerciaux enregistrés dans les actions
    @Query("""
        select distinct a.commercial
        from Action a
        where a.commercial is not null
        order by a.commercial
    """)
    List<String> findDistinctCommerciaux();

    // Retourne la liste distincte des lieux/organismes enregistrés dans les actions
    @Query("""
        select distinct a.lieuOrganisme
        from Action a
        where a.lieuOrganisme is not null
        order by a.lieuOrganisme
    """)
    List<String> findDistinctLieuxOrganismes();

    // Retourne les actions des médecins ayant des données lab importées
    @Query("""
        select a
        from Action a
        join fetch a.medecin m
        where exists (
            select e.id from ExtractionDonnees e where e.medecin = m
        )
    """)
    List<Action> findAllForImportedDoctors();

    // Supprime les actions auto-générées non touchées par un commercial (sans retour terrain)
    // NOT EXISTS est placé au niveau global pour protéger les deux branches (IMPORT_AUTO et libellés auto)
    @Modifying
    @Query(value = """
        delete from actions
        where id in (
            select a.id from actions a
            where (
                a.source_recommandation = 'IMPORT_AUTO'
                or (a.source_recommandation is null
                    and a.action_recommandee in (
                        'visite_reconnaissance', 'fidelisation',
                        'visite_diagnostic_surveillance', 'visite_diagnostic',
                        'visite_qualification', 'visite_prospection',
                        'visite suivi progression', 'visite suivi régulier',
                        'visite diagnostic surveillance', 'visite diagnostic', 'visite onboarding'
                    )
                    and a.etat_action = 'PLANIFIEE'
                    and a.reclamation_id is null
                    and a.decision_recommandation is null
                    and a.action_parente_id is null
                    and a.statut_plan_retention is null
                    and (a.is_reserved = false or a.is_reserved is null)
                )
            )
            and not exists (
                select 1 from retours_terrain rt where rt.action_id = a.id
            )
        )
        """, nativeQuery = true)
    int deleteUntouchedImportGeneratedActions();

    @Query("""
            select case when count(a) > 0 then true else false end
            from Action a
            where a.medecin.id = :medecinId
                and a.typeEtapeRetention in :etapes
                and a.statutPlanRetention = :statut
    """)
    boolean existsRetentionRecovery(
            @Param("medecinId") Long medecinId,
            @Param("etapes") List<TypeEtapeRetention> etapes,
            @Param("statut") StatutPlanRetention statut
    );
}
