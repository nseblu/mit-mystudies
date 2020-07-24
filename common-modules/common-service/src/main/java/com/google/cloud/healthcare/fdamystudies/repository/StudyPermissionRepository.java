/*
 * Copyright 2020 Google LLC
 *
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE file or at
 * https://opensource.org/licenses/MIT.
 */

package com.google.cloud.healthcare.fdamystudies.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import com.google.cloud.healthcare.fdamystudies.model.StudyPermissionEntity;

@Repository
@ConditionalOnProperty(
    value = "participant.manager.repository.enabled",
    havingValue = "true",
    matchIfMissing = false)
public interface StudyPermissionRepository extends JpaRepository<StudyPermissionEntity, String> {

  @Query(
      "SELECT sp from StudyPermissionEntity sp "
          + "where sp.study.id=:studyId and sp.urAdminUser.id=:userId")
  public Optional<StudyPermissionEntity> findByStudyIdAndUserId(String studyId, String userId);

  @Query("SELECT sp from StudyPermissionEntity sp where sp.study.id=:studyId")
  public List<StudyPermissionEntity> findByStudyId(String studyId);

  @Query(
      "SELECT sp FROM StudyPermissionEntity sp "
          + "WHERE  sp.study.id IN (:usersStudyIds) and  sp.urAdminUser.id=:userId")
  public List<StudyPermissionEntity> findStudyPermissionsOfUserByStudyIds(
      @Param("usersStudyIds") List<String> usersStudyIds, String userId);

  @Query(
      "SELECT sp FROM StudyPermissionEntity sp where sp.urAdminUser.id in (:siteAdminIdList) and sp.study.id in (:studyIdList)")
  public List<StudyPermissionEntity> findByByUserIdsAndStudyIds(
      @Param("siteAdminIdList") List<String> siteAdminIdList,
      @Param("studyIdList") List<String> studyIdList);

  @Query("SELECT sp from StudyPermissionEntity sp where sp.urAdminUser.id=:adminId")
  public List<StudyPermissionEntity> findByAdminUser(String adminId);

  @Transactional
  @Modifying
  @Query("DELETE from StudyPermissionEntity sp where sp.urAdminUser.id=:adminId")
  public void deleteByAdminUserId(String adminId);
}
