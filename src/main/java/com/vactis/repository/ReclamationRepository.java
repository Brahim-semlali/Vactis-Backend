package com.vactis.repository;

import com.vactis.model.medecin.Medecin;
import com.vactis.model.reclamation.CategorieReclamation;
import com.vactis.model.reclamation.PrioriteReclamation;
import com.vactis.model.reclamation.Reclamation;
import com.vactis.model.reclamation.StatutReclamation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReclamationRepository extends JpaRepository<Reclamation, Long> {

    List<Reclamation> findByMedecinOrderByDateDeclarationDesc(Medecin medecin);

    List<Reclamation> findByStatutOrderByDateDeclarationDesc(StatutReclamation statut);

    long countByStatut(StatutReclamation statut);

    @Query("""
        select r from Reclamation r
        where (:statut is null or r.statut = :statut)
          and (:categorie is null or r.categorie = :categorie)
          and (:priorite is null or r.priorite = :priorite)
          and (:search is null or :search = '' or
               lower(concat(r.medecin.nom, ' ', r.medecin.prenom)) like lower(concat('%', :search, '%')) or
               lower(r.commercialDeclarant) like lower(concat('%', :search, '%')) or
               lower(r.description) like lower(concat('%', :search, '%')))
        order by r.dateDeclaration desc, r.createdAt desc
    """)
    List<Reclamation> searchReclamations(
            @Param("search") String search,
            @Param("statut") StatutReclamation statut,
            @Param("categorie") CategorieReclamation categorie,
            @Param("priorite") PrioriteReclamation priorite
    );
}
