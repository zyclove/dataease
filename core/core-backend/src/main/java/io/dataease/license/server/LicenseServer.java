package io.dataease.license.server;

import io.dataease.api.license.LicenseApi;
import io.dataease.api.license.dto.LicenseRequest;
import io.dataease.exception.DEException;
import io.dataease.license.bo.F2CLicResult;
import io.dataease.license.manage.CoreLicManage;
import io.dataease.license.manage.F2CLicManage;
import io.dataease.license.utils.LicenseUtil;
import io.dataease.utils.AuthUtils;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/license")
public class LicenseServer implements LicenseApi {

    private static final String product = "DataEase v2";
    @Resource
    private CoreLicManage coreLicManage;

    @Resource
    private F2CLicManage f2CLicManage;


    @Override
    public F2CLicResult update(LicenseRequest request) {
        return f2CLicManage.updateLicense(product, request.getLicense());
    }

    @Override
    public F2CLicResult validate(LicenseRequest request) {
        if (StringUtils.isBlank(request.getLicense())) {
            return f2CLicManage.validate();
        }
        return f2CLicManage.validate(product, request.getLicense());
    }

    @Override
    public String version() {
        return coreLicManage.getVersion();
    }

    @Override
    public void revert() {
        F2CLicResult f2CLicResult = null;
        if (!AuthUtils.isSysAdmin() || ObjectUtils.isEmpty(f2CLicResult = LicenseUtil.get()) || f2CLicResult.getStatus() != F2CLicResult.Status.expired) {
            DEException.throwException("不能进行还原操作!");
        }
        f2CLicManage.revert();
    }
}
