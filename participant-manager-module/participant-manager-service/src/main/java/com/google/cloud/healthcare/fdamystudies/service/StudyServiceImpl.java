/*
 * Copyright 2020 Google LLC
 *
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE file or at
 * https://opensource.org/licenses/MIT.
 */
package com.google.cloud.healthcare.fdamystudies.service;

import static com.google.cloud.healthcare.fdamystudies.common.CommonConstants.OPEN_STUDY;
import static com.google.cloud.healthcare.fdamystudies.common.ParticipantManagerEvent.STUDY_PARTICIPANT_REGISTRY_VIEWED;

import com.google.cloud.healthcare.fdamystudies.beans.AuditLogEventRequest;
import com.google.cloud.healthcare.fdamystudies.beans.ParticipantDetail;
import com.google.cloud.healthcare.fdamystudies.beans.ParticipantRegistryDetail;
import com.google.cloud.healthcare.fdamystudies.beans.ParticipantRegistryResponse;
import com.google.cloud.healthcare.fdamystudies.beans.StudyDetails;
import com.google.cloud.healthcare.fdamystudies.beans.StudyResponse;
import com.google.cloud.healthcare.fdamystudies.common.ErrorCode;
import com.google.cloud.healthcare.fdamystudies.common.MessageCode;
import com.google.cloud.healthcare.fdamystudies.common.ParticipantManagerAuditLogHelper;
import com.google.cloud.healthcare.fdamystudies.common.Permission;
import com.google.cloud.healthcare.fdamystudies.exceptions.ErrorCodeException;
import com.google.cloud.healthcare.fdamystudies.mapper.ParticipantMapper;
import com.google.cloud.healthcare.fdamystudies.model.EnrolledInvitedCountForStudy;
import com.google.cloud.healthcare.fdamystudies.model.SiteCount;
import com.google.cloud.healthcare.fdamystudies.model.StudyAppDetails;
import com.google.cloud.healthcare.fdamystudies.model.StudyCount;
import com.google.cloud.healthcare.fdamystudies.model.StudyEntity;
import com.google.cloud.healthcare.fdamystudies.model.StudyInfo;
import com.google.cloud.healthcare.fdamystudies.model.StudyParticipantDetails;
import com.google.cloud.healthcare.fdamystudies.model.UserRegAdminEntity;
import com.google.cloud.healthcare.fdamystudies.repository.SiteRepository;
import com.google.cloud.healthcare.fdamystudies.repository.StudyRepository;
import com.google.cloud.healthcare.fdamystudies.repository.UserRegAdminRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudyServiceImpl implements StudyService {
  private XLogger logger = XLoggerFactory.getXLogger(StudyServiceImpl.class.getName());

  @Autowired private StudyRepository studyRepository;

  @Autowired private SiteRepository siteRepository;

  @Autowired private ParticipantManagerAuditLogHelper participantManagerHelper;

  @Autowired private UserRegAdminRepository userRegAdminRepository;

  @Override
  @Transactional(readOnly = true)
  public StudyResponse getStudies(String userId, Integer limit, Integer offset, String searchTerm) {
    logger.entry("getStudies(String userId)");

    Optional<UserRegAdminEntity> optUserRegAdminEntity = userRegAdminRepository.findById(userId);
    if (!(optUserRegAdminEntity.isPresent())) {
      throw new ErrorCodeException(ErrorCode.USER_NOT_FOUND);
    }

    if (optUserRegAdminEntity.get().isSuperAdmin()) {
      StudyResponse studyResponse =
          getStudiesForSuperAdmin(
              optUserRegAdminEntity.get(), limit, offset, StringUtils.defaultString(searchTerm));
      logger.exit(
          String.format("total studies for superadmin=%d", studyResponse.getStudies().size()));
      return studyResponse;
    }

    List<StudyInfo> studyDetails =
        studyRepository.getStudyDetails(
            userId, limit, offset, StringUtils.defaultString(searchTerm));

    if (CollectionUtils.isEmpty(studyDetails)) {
      throw new ErrorCodeException(ErrorCode.NO_STUDIES_FOUND);
    }

    List<EnrolledInvitedCountForStudy> enrolledInvitedCountList =
        studyRepository.getEnrolledInvitedCountByUserId(userId);
    Map<String, EnrolledInvitedCountForStudy> enrolledInvitedCountMap =
        CollectionUtils.emptyIfNull(enrolledInvitedCountList)
            .stream()
            .collect(
                Collectors.toMap(EnrolledInvitedCountForStudy::getStudyId, Function.identity()));

    List<EnrolledInvitedCountForStudy> enrolledInvitedCountListForOpenStudy =
        studyRepository.getInvitedEnrolledCountForOpenStudy(userId);
    Map<String, EnrolledInvitedCountForStudy> enrolledInvitedCountMapOfOpenStudy =
        CollectionUtils.emptyIfNull(enrolledInvitedCountListForOpenStudy)
            .stream()
            .collect(
                Collectors.toMap(EnrolledInvitedCountForStudy::getStudyId, Function.identity()));
    enrolledInvitedCountMap.putAll(enrolledInvitedCountMapOfOpenStudy);

    List<StudyCount> siteCounts = studyRepository.getSiteCount(userId);
    Map<String, StudyCount> sitesCountMap =
        siteCounts.stream().collect(Collectors.toMap(StudyCount::getStudyId, Function.identity()));

    return prepareStudyResponse(
        studyDetails, sitesCountMap, enrolledInvitedCountMap, optUserRegAdminEntity.get());
  }

  private StudyResponse getStudiesForSuperAdmin(
      UserRegAdminEntity userRegAdminEntity, Integer limit, Integer offset, String searchTerm) {

    List<StudyCount> studyInvitedCountList = studyRepository.findInvitedCountByStudyId();
    Map<String, StudyCount> studyInvitedCountMap =
        studyInvitedCountList
            .stream()
            .collect(Collectors.toMap(StudyCount::getStudyId, Function.identity()));

    List<StudyCount> studyEnrolledCountList = studyRepository.findEnrolledCountByStudyId();
    Map<String, StudyCount> studyEnrolledCountMap =
        studyEnrolledCountList
            .stream()
            .collect(Collectors.toMap(StudyCount::getStudyId, Function.identity()));

    List<SiteCount> sitesList = siteRepository.findStudySitesCount();
    Map<String, SiteCount> sitesPerStudyMap =
        sitesList.stream().collect(Collectors.toMap(SiteCount::getStudyId, Function.identity()));
    List<StudyEntity> studies = studyRepository.findAll(limit, offset, searchTerm);
    List<StudyDetails> studyDetailsList = new ArrayList<>();
    for (StudyEntity study : studies) {
      if (sitesPerStudyMap.containsKey(study.getId())) {
        StudyDetails studyDetail = new StudyDetails();
        studyDetail.setId(study.getId());
        studyDetail.setCustomId(study.getCustomId());
        studyDetail.setName(study.getName());
        studyDetail.setType(study.getType());
        studyDetail.setLogoImageUrl(study.getLogoImageUrl());
        SiteCount siteCount = sitesPerStudyMap.get(study.getId());
        if (siteCount != null && siteCount.getCount() != null) {
          studyDetail.setSitesCount(siteCount.getCount());
        }
        studyDetail.setStudyPermission(Permission.EDIT.value());
        Long enrolledCount = getCount(studyEnrolledCountMap, study.getId());
        Long invitedCount = getCount(studyInvitedCountMap, study.getId());
        studyDetail.setEnrolled(enrolledCount);
        studyDetail.setInvited(invitedCount);
        if (studyDetail.getInvited() != 0
            && (studyDetail.getType().equals(OPEN_STUDY)
                || studyDetail.getInvited() >= studyDetail.getEnrolled())) {
          Double percentage =
              (Double.valueOf(studyDetail.getEnrolled()) * 100)
                  / Double.valueOf(studyDetail.getInvited());
          studyDetail.setEnrollmentPercentage(percentage);
        }
        studyDetailsList.add(studyDetail);
      }
    }
    return new StudyResponse(
        MessageCode.GET_STUDIES_SUCCESS, studyDetailsList, userRegAdminEntity.isSuperAdmin());
  }

  private Long getCount(Map<String, StudyCount> map, String studyId) {
    if (map.containsKey(studyId)) {
      return map.get(studyId).getCount();
    }
    return 0L;
  }

  private StudyResponse prepareStudyResponse(
      List<StudyInfo> studyList,
      Map<String, StudyCount> sitesCountMap,
      Map<String, EnrolledInvitedCountForStudy> enrolledInvitedCountMap,
      UserRegAdminEntity userRegAdminEntity) {
    List<StudyDetails> studies = new ArrayList<>();
    for (StudyInfo study : studyList) {
      StudyDetails studyDetail = new StudyDetails();

      studyDetail.setId(study.getStudyId());
      studyDetail.setCustomId(study.getCustomId());
      studyDetail.setName(study.getStudyName());
      studyDetail.setType(study.getType());
      studyDetail.setLogoImageUrl(study.getLogoImageUrl());
      studyDetail.setStudyPermission(study.getEdit());

      studyDetail.setSitesCount(
          sitesCountMap.containsKey(study.getStudyId())
              ? sitesCountMap.get(study.getStudyId()).getCount()
              : 0L);

      calculateEnrollmentPercentage(enrolledInvitedCountMap, study, studyDetail);
      studies.add(studyDetail);
    }

    LongSummaryStatistics totalSitePermission =
        sitesCountMap.values().stream().mapToLong(StudyCount::getCount).summaryStatistics();

    StudyResponse studyResponse =
        new StudyResponse(
            MessageCode.GET_STUDIES_SUCCESS,
            studies,
            totalSitePermission.getSum(),
            userRegAdminEntity.isSuperAdmin());
    logger.exit(String.format("total studies=%d", studyResponse.getStudies().size()));
    return studyResponse;
  }

  private void calculateEnrollmentPercentage(
      Map<String, EnrolledInvitedCountForStudy> enrolledInvitedCountMap,
      StudyInfo study,
      StudyDetails studyDetail) {
    long studyInvitedCount = 0L;
    Long studyEnrolledCount = 0L;

    EnrolledInvitedCountForStudy enrolledInvitedCount =
        enrolledInvitedCountMap.get(study.getStudyId());

    if (enrolledInvitedCount != null) {
      studyInvitedCount = enrolledInvitedCount.getInvitedCount();
      studyEnrolledCount = enrolledInvitedCount.getEnrolledCount();
    }

    studyDetail.setEnrolled(studyEnrolledCount);
    studyDetail.setInvited(studyInvitedCount);
    if (studyDetail.getInvited() != 0
        && (studyDetail.getType().equals(OPEN_STUDY)
            || studyDetail.getInvited() >= studyDetail.getEnrolled())) {
      Double percentage =
          (Double.valueOf(studyDetail.getEnrolled()) * 100)
              / Double.valueOf(studyDetail.getInvited());
      studyDetail.setEnrollmentPercentage(percentage);
    }
  }

  @Override
  public ParticipantRegistryResponse getStudyParticipants(
      String userId,
      String studyId,
      String[] excludeParticipantStudyStatus,
      AuditLogEventRequest auditRequest,
      Integer limit,
      Integer offset,
      String orderByCondition,
      String searchTerm) {
    logger.entry("getStudyParticipants(String userId, String studyId)");
    // validations

    Optional<UserRegAdminEntity> optUserRegAdminEntity = userRegAdminRepository.findById(userId);
    UserRegAdminEntity user =
        optUserRegAdminEntity.orElseThrow(() -> new ErrorCodeException(ErrorCode.USER_NOT_FOUND));

    Optional<StudyAppDetails> optStudyAppDetails = null;
    if (user.isSuperAdmin()) {
      optStudyAppDetails = studyRepository.getStudyParticipantForSuperAdmin(studyId);
    } else {
      optStudyAppDetails = studyRepository.getStudyParticipant(studyId, userId);
    }

    StudyAppDetails studyAppDetails =
        optStudyAppDetails.orElseThrow(() -> new ErrorCodeException(ErrorCode.STUDY_NOT_FOUND));

    if (StringUtils.isEmpty(studyAppDetails.getAppName())) {
      throw new ErrorCodeException(ErrorCode.APP_NOT_FOUND);
    }

    ParticipantRegistryDetail participantRegistryDetail =
        ParticipantMapper.fromStudyAppDetails(studyAppDetails, user);

    return prepareRegistryParticipantResponse(
        participantRegistryDetail,
        userId,
        studyId,
        excludeParticipantStudyStatus,
        auditRequest,
        limit,
        offset,
        orderByCondition,
        searchTerm);
  }

  private ParticipantRegistryResponse prepareRegistryParticipantResponse(
      ParticipantRegistryDetail participantRegistryDetail,
      String userId,
      String studyId,
      String[] excludeParticipantStudyStatus,
      AuditLogEventRequest auditRequest,
      Integer limit,
      Integer offset,
      String orderByCondition,
      String searchTerm) {

    List<ParticipantDetail> registryParticipants = new ArrayList<>();

    List<StudyParticipantDetails> studyParticipantDetails =
        studyRepository.getStudyParticipantDetails(
            studyId, limit, offset, orderByCondition, StringUtils.defaultString(searchTerm));
    for (StudyParticipantDetails participantDetails : studyParticipantDetails) {
      if (ArrayUtils.contains(
          excludeParticipantStudyStatus, participantDetails.getEnrolledStatus())) {
        continue;
      }
      ParticipantDetail participantDetail =
          ParticipantMapper.fromParticipantStudy(participantDetails);
      registryParticipants.add(participantDetail);
    }

    if (StringUtils.equals(orderByCondition, "enrollmentStatus_asc")) {
      registryParticipants.sort(
          (ParticipantDetail s1, ParticipantDetail s2) ->
              s1.getEnrollmentStatus().compareTo(s2.getEnrollmentStatus()));
    } else if (StringUtils.equals(orderByCondition, "enrollmentStatus_desc")) {
      registryParticipants.sort(
          (ParticipantDetail s1, ParticipantDetail s2) ->
              s2.getEnrollmentStatus().compareTo(s1.getEnrollmentStatus()));
    }

    participantRegistryDetail.setRegistryParticipants(registryParticipants);
    Long participantCount =
        studyRepository.countParticipantsByStudyIdAndSearchTerm(studyId, searchTerm);
    ParticipantRegistryResponse participantRegistryResponse =
        new ParticipantRegistryResponse(
            MessageCode.GET_PARTICIPANT_REGISTRY_SUCCESS, participantRegistryDetail);
    participantRegistryResponse.setTotalParticipantCount(participantCount);

    auditRequest.setUserId(userId);
    auditRequest.setStudyId(studyId);
    auditRequest.setAppId(participantRegistryDetail.getAppId());
    participantManagerHelper.logEvent(STUDY_PARTICIPANT_REGISTRY_VIEWED, auditRequest);

    logger.exit(String.format("message=%s", participantRegistryResponse.getMessage()));
    return participantRegistryResponse;
  }
}
