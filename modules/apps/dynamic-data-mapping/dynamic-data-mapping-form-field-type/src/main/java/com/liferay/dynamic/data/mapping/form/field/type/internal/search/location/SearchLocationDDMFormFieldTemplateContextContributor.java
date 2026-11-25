/**
 * SPDX-FileCopyrightText: (c) 2000 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.dynamic.data.mapping.form.field.type.internal.search.location;

import com.liferay.dynamic.data.mapping.constants.DDMActionKeys;
import com.liferay.dynamic.data.mapping.form.field.type.DDMFormFieldTemplateContextContributor;
import com.liferay.dynamic.data.mapping.form.field.type.constants.DDMFormFieldTypeConstants;
import com.liferay.dynamic.data.mapping.form.field.type.internal.util.DDMFormFieldTypeUtil;
import com.liferay.dynamic.data.mapping.model.DDMFormField;
import com.liferay.dynamic.data.mapping.model.DDMFormInstance;
import com.liferay.dynamic.data.mapping.model.DDMFormInstanceSettings;
import com.liferay.dynamic.data.mapping.model.LocalizedValue;
import com.liferay.dynamic.data.mapping.render.DDMFormFieldRenderingContext;
import com.liferay.dynamic.data.mapping.service.DDMFormInstanceLocalService;
import com.liferay.google.places.util.GooglePlacesUtil;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.json.JSONException;
import com.liferay.portal.kernel.json.JSONFactory;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.json.JSONUtil;
import com.liferay.portal.kernel.language.Language;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.security.permission.ActionKeys;
import com.liferay.portal.kernel.security.permission.PermissionChecker;
import com.liferay.portal.kernel.security.permission.resource.ModelResourcePermission;
import com.liferay.portal.kernel.service.GroupLocalService;
import com.liferay.portal.kernel.service.permission.GroupPermissionUtil;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.util.ResourceBundleUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.util.WebKeys;
import com.liferay.portal.security.key.secret.SecretResolver;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Locale;
import java.util.Map;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * @author Rodrigo Paulino
 */
@Component(
	property = "ddm.form.field.type.name=" + DDMFormFieldTypeConstants.SEARCH_LOCATION,
	service = DDMFormFieldTemplateContextContributor.class
)
public class SearchLocationDDMFormFieldTemplateContextContributor
	implements DDMFormFieldTemplateContextContributor {

	@Override
	public Map<String, Object> getParameters(
		DDMFormField ddmFormField,
		DDMFormFieldRenderingContext ddmFormFieldRenderingContext) {

		return HashMapBuilder.<String, Object>put(
			"googlePlacesAPIKey",
			_getGooglePlacesAPIKey(ddmFormField, ddmFormFieldRenderingContext)
		).put(
			"labels",
			_getLabelsJSONObject(
				ddmFormFieldRenderingContext.getLocale(),
				_getVisibleFields(ddmFormField, ddmFormFieldRenderingContext))
		).put(
			"layout",
			DDMFormFieldTypeUtil.getPropertyValue(
				ddmFormField, ddmFormFieldRenderingContext.getLocale(),
				"layout")
		).put(
			"placeholder",
			DDMFormFieldTypeUtil.getPropertyValue(
				ddmFormField, ddmFormFieldRenderingContext.getLocale(),
				"placeholder")
		).put(
			"visibleFields",
			DDMFormFieldTypeUtil.getPropertyValue(
				ddmFormField, ddmFormFieldRenderingContext.getLocale(),
				"visibleFields")
		).build();
	}

	private String _getGooglePlacesAPIKey(
		DDMFormField ddmFormField,
		DDMFormFieldRenderingContext ddmFormFieldRenderingContext) {

		String googlePlacesAPIKey = GetterUtil.getString(
			ddmFormField.getProperty("googlePlacesAPIKey"));

		if (Validator.isNotNull(googlePlacesAPIKey)) {
			return googlePlacesAPIKey;
		}

		HttpServletRequest httpServletRequest =
			ddmFormFieldRenderingContext.getHttpServletRequest();

		ThemeDisplay themeDisplay =
			(ThemeDisplay)httpServletRequest.getAttribute(
				WebKeys.THEME_DISPLAY);

		long companyId = themeDisplay.getCompanyId();

		return _secretResolver.resolve(
			companyId,
			GooglePlacesUtil.getGooglePlacesAPIKey(
				companyId,
				_getGroupId(
					companyId, ddmFormFieldRenderingContext,
					themeDisplay.getPermissionChecker()),
				_groupLocalService));
	}

	private long _getGroupId(
		long companyId,
		DDMFormFieldRenderingContext ddmFormFieldRenderingContext,
		PermissionChecker permissionChecker) {

		Group group = _groupLocalService.fetchGroup(
			GetterUtil.getLong(
				ddmFormFieldRenderingContext.getProperty("groupId")));

		if ((group == null) || (group.getCompanyId() != companyId)) {
			return 0;
		}

		try {
			if (GroupPermissionUtil.contains(
					permissionChecker, group, ActionKeys.VIEW) ||
				_hasFormInstancePermission(
					ddmFormFieldRenderingContext.getDDMFormInstanceId(),
					group.getGroupId(), permissionChecker)) {

				return group.getGroupId();
			}
		}
		catch (PortalException portalException) {
			if (_log.isWarnEnabled()) {
				_log.warn(portalException);
			}
		}

		return 0;
	}

	private JSONObject _getLabelsJSONObject(
		Locale locale, String[] visibleFields) {

		JSONObject jsonObject = _jsonFactory.createJSONObject();

		for (String visibleField : visibleFields) {
			jsonObject.put(
				visibleField,
				_language.get(
					ResourceBundleUtil.getModuleAndPortalResourceBundle(
						locale, getClass()),
					visibleField));
		}

		return jsonObject;
	}

	private String[] _getVisibleFields(
		DDMFormField ddmFormField,
		DDMFormFieldRenderingContext ddmFormFieldRenderingContext) {

		LocalizedValue visibleFields = (LocalizedValue)ddmFormField.getProperty(
			"visibleFields");

		try {
			return JSONUtil.toStringArray(
				_jsonFactory.createJSONArray(
					visibleFields.getString(
						ddmFormFieldRenderingContext.getLocale())));
		}
		catch (JSONException jsonException) {
			if (_log.isDebugEnabled()) {
				_log.debug(jsonException);
			}
		}

		return new String[0];
	}

	private boolean _hasFormInstancePermission(
			long ddmFormInstanceId, long groupId,
			PermissionChecker permissionChecker)
		throws PortalException {

		DDMFormInstance ddmFormInstance =
			_ddmFormInstanceLocalService.fetchFormInstance(ddmFormInstanceId);

		if ((ddmFormInstance == null) ||
			(ddmFormInstance.getGroupId() != groupId)) {

			return false;
		}

		DDMFormInstanceSettings ddmFormInstanceSettings =
			ddmFormInstance.getSettingsModel();

		if (!ddmFormInstanceSettings.published() ||
			(ddmFormInstanceSettings.requireAuthentication() &&
			 !permissionChecker.isSignedIn())) {

			return false;
		}

		if (_ddmFormInstanceModelResourcePermission.contains(
				permissionChecker, ddmFormInstance,
				DDMActionKeys.ADD_FORM_INSTANCE_RECORD)) {

			return true;
		}

		return _ddmFormInstanceModelResourcePermission.contains(
			permissionChecker, ddmFormInstance, ActionKeys.VIEW);
	}

	private static final Log _log = LogFactoryUtil.getLog(
		SearchLocationDDMFormFieldTemplateContextContributor.class);

	@Reference
	private DDMFormInstanceLocalService _ddmFormInstanceLocalService;

	@Reference(
		target = "(model.class.name=com.liferay.dynamic.data.mapping.model.DDMFormInstance)"
	)
	private ModelResourcePermission<DDMFormInstance>
		_ddmFormInstanceModelResourcePermission;

	@Reference
	private GroupLocalService _groupLocalService;

	@Reference
	private JSONFactory _jsonFactory;

	@Reference
	private Language _language;

	@Reference
	private SecretResolver _secretResolver;

}