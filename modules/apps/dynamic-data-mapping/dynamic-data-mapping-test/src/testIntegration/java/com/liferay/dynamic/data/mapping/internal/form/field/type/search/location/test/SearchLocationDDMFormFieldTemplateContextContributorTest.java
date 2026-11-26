/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.dynamic.data.mapping.internal.form.field.type.search.location.test;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.dynamic.data.mapping.constants.DDMActionKeys;
import com.liferay.dynamic.data.mapping.form.field.type.DDMFormFieldTemplateContextContributor;
import com.liferay.dynamic.data.mapping.model.DDMFormInstance;
import com.liferay.dynamic.data.mapping.model.UnlocalizedValue;
import com.liferay.dynamic.data.mapping.render.DDMFormFieldRenderingContext;
import com.liferay.dynamic.data.mapping.storage.DDMFormFieldValue;
import com.liferay.dynamic.data.mapping.storage.DDMFormValues;
import com.liferay.dynamic.data.mapping.test.util.DDMFormInstanceTestUtil;
import com.liferay.dynamic.data.mapping.test.util.DDMFormTestUtil;
import com.liferay.dynamic.data.mapping.test.util.DDMFormValuesTestUtil;
import com.liferay.google.places.constants.GooglePlacesWebKeys;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.model.GroupConstants;
import com.liferay.portal.kernel.model.ResourceConstants;
import com.liferay.portal.kernel.model.Role;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.model.role.RoleConstants;
import com.liferay.portal.kernel.security.permission.PermissionChecker;
import com.liferay.portal.kernel.security.permission.PermissionCheckerFactoryUtil;
import com.liferay.portal.kernel.service.CompanyLocalService;
import com.liferay.portal.kernel.service.GroupLocalService;
import com.liferay.portal.kernel.service.ResourcePermissionLocalService;
import com.liferay.portal.kernel.service.RoleLocalService;
import com.liferay.portal.kernel.service.UserLocalService;
import com.liferay.portal.kernel.test.rule.DeleteAfterTestRun;
import com.liferay.portal.kernel.test.util.GroupTestUtil;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.test.util.UserTestUtil;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.UnicodeProperties;
import com.liferay.portal.kernel.util.WebKeys;
import com.liferay.portal.test.rule.Inject;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.mock.web.MockHttpServletRequest;

/**
 * @author Paulo Pereira
 */
@RunWith(Arquillian.class)
public class SearchLocationDDMFormFieldTemplateContextContributorTest {

	@ClassRule
	@Rule
	public static final LiferayIntegrationTestRule liferayIntegrationTestRule =
		new LiferayIntegrationTestRule();

	@Before
	public void setUp() throws Exception {
		_group = GroupTestUtil.addGroup();

		_group.setType(GroupConstants.TYPE_SITE_PRIVATE);

		UnicodeProperties typeSettingsUnicodeProperties =
			_group.getTypeSettingsProperties();

		typeSettingsUnicodeProperties.setProperty(
			GooglePlacesWebKeys.GOOGLE_PLACES_API_KEY, _GOOGLE_PLACES_API_KEY);

		_group.setTypeSettingsProperties(typeSettingsUnicodeProperties);

		_group = _groupLocalService.updateGroup(_group);
	}

	@Test
	public void testGetGooglePlacesAPIKeyWhenFormInstanceIsPublished()
		throws Exception {

		DDMFormInstance ddmFormInstance = _addDDMFormInstance();

		Assert.assertEquals(
			_GOOGLE_PLACES_API_KEY,
			_getGooglePlacesAPIKey(
				ddmFormInstance.getFormInstanceId(),
				PermissionCheckerFactoryUtil.create(
					_userLocalService.getGuestUser(_group.getCompanyId()))));
	}

	@Test
	public void testGetGooglePlacesAPIKeyWhenUserCanViewGroup()
		throws Exception {

		_user = UserTestUtil.addGroupUser(_group, RoleConstants.SITE_MEMBER);

		Assert.assertEquals(
			_GOOGLE_PLACES_API_KEY,
			_getGooglePlacesAPIKey(
				0, PermissionCheckerFactoryUtil.create(_user)));
	}

	@Test
	public void testGetGooglePlacesAPIKeyWhenUserCannotViewGroup()
		throws Exception {

		Assert.assertNotEquals(
			_GOOGLE_PLACES_API_KEY,
			_getGooglePlacesAPIKey(
				0,
				PermissionCheckerFactoryUtil.create(
					_userLocalService.getGuestUser(_group.getCompanyId()))));
	}

	private DDMFormInstance _addDDMFormInstance() throws Exception {
		DDMFormValues settingsDDMFormValues =
			DDMFormInstanceTestUtil.createSettingsDDMFormValues();

		for (DDMFormFieldValue ddmFormFieldValue :
				settingsDDMFormValues.getDDMFormFieldValues()) {

			if (!Objects.equals(ddmFormFieldValue.getName(), "published")) {
				continue;
			}

			ddmFormFieldValue.setValue(new UnlocalizedValue("true"));
		}

		DDMFormInstance ddmFormInstance =
			DDMFormInstanceTestUtil.addDDMFormInstance(
				DDMFormTestUtil.createDDMForm("text"), _group,
				settingsDDMFormValues, TestPropsValues.getUserId());

		Role role = _roleLocalService.getRole(
			_group.getCompanyId(), RoleConstants.GUEST);

		_resourcePermissionLocalService.setResourcePermissions(
			_group.getCompanyId(), DDMFormInstance.class.getName(),
			ResourceConstants.SCOPE_INDIVIDUAL,
			String.valueOf(ddmFormInstance.getFormInstanceId()),
			role.getRoleId(),
			new String[] {DDMActionKeys.ADD_FORM_INSTANCE_RECORD});

		return ddmFormInstance;
	}

	private String _getGooglePlacesAPIKey(
			long ddmFormInstanceId, PermissionChecker permissionChecker)
		throws Exception {

		ThemeDisplay themeDisplay = new ThemeDisplay();

		themeDisplay.setCompany(
			_companyLocalService.getCompany(_group.getCompanyId()));
		themeDisplay.setPermissionChecker(permissionChecker);

		MockHttpServletRequest mockHttpServletRequest =
			new MockHttpServletRequest();

		mockHttpServletRequest.setAttribute(
			WebKeys.THEME_DISPLAY, themeDisplay);

		DDMFormFieldRenderingContext ddmFormFieldRenderingContext =
			new DDMFormFieldRenderingContext();

		ddmFormFieldRenderingContext.setDDMFormInstanceId(ddmFormInstanceId);
		ddmFormFieldRenderingContext.setHttpServletRequest(
			mockHttpServletRequest);
		ddmFormFieldRenderingContext.setLocale(LocaleUtil.US);
		ddmFormFieldRenderingContext.setProperty(
			"groupId", _group.getGroupId());

		Map<String, Object> parameters =
			_ddmFormFieldTemplateContextContributor.getParameters(
				DDMFormTestUtil.createSearchLocationDDMFormField(
					DDMFormValuesTestUtil.createLocalizedValue(
						Arrays.toString(new String[] {"two-columns"}),
						LocaleUtil.US),
					RandomTestUtil.randomString(),
					DDMFormValuesTestUtil.createLocalizedValue(
						Arrays.toString(new String[] {"city", "country"}),
						LocaleUtil.US)),
				ddmFormFieldRenderingContext);

		return (String)parameters.get("googlePlacesAPIKey");
	}

	private static final String _GOOGLE_PLACES_API_KEY =
		RandomTestUtil.randomString();

	@Inject
	private CompanyLocalService _companyLocalService;

	@Inject(filter = "ddm.form.field.type.name=search_location")
	private DDMFormFieldTemplateContextContributor
		_ddmFormFieldTemplateContextContributor;

	@DeleteAfterTestRun
	private Group _group;

	@Inject
	private GroupLocalService _groupLocalService;

	@Inject
	private ResourcePermissionLocalService _resourcePermissionLocalService;

	@Inject
	private RoleLocalService _roleLocalService;

	@DeleteAfterTestRun
	private User _user;

	@Inject
	private UserLocalService _userLocalService;

}