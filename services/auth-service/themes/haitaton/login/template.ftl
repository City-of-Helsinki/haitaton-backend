<#macro registrationLayout bodyClass="" displayInfo=false displayMessage=true displayRequiredFields=false showAnotherWayIfPresent=true>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"  "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml" class="${properties.kcHtmlClass!}">

<head>
    <meta charset="utf-8">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
    <meta name="robots" content="noindex, nofollow">

    <#if properties.meta?has_content>
        <#list properties.meta?split(' ') as meta>
            <meta name="${meta?split('==')[0]}" content="${meta?split('==')[1]}"/>
        </#list>
    </#if>
    <title>${msg("loginTitle",(realm.displayName!''))}</title>
    <link rel="icon" href="${url.resourcesPath}/img/favicon.ico" />
    <#if properties.stylesCommon?has_content>
        <#list properties.stylesCommon?split(' ') as style>
            <link href="${url.resourcesCommonPath}/${style}" rel="stylesheet" />
        </#list>
    </#if>
    <#if properties.styles?has_content>
        <#list properties.styles?split(' ') as style>
            <link href="${url.resourcesPath}/${style}" rel="stylesheet" />
        </#list>
    </#if>
    <#if properties.scripts?has_content>
        <#list properties.scripts?split(' ') as script>
            <script src="${url.resourcesPath}/${script}" type="text/javascript"></script>
        </#list>
    </#if>
    <#if scripts??>
        <#list scripts as script>
            <script src="${script}" type="text/javascript"></script>
        </#list>
    </#if>
</head>

<body class="${properties.kcBodyClass!}">
<div class="${properties.kcLoginClass!}">
    <div id="kc-header" class="${properties.kcHeaderClass!}">
        <div id="kc-header-wrapper" class="${properties.kcHeaderWrapperClass!}">
        <svg
            viewBox="0 0 78 36"
            title="Helsingin kaupunki"
            role="img"
            xmlns="http://www.w3.org/2000/svg"
            class="Logo-module_logo__Y2mwP Navigation-module_logo__1sEy6"
            aria-hidden="true"
            >
            <path
                d="M75.753 2.251v20.7c0 3.95-3.275 7.178-7.31 7.178h-22.26c-2.674 0-5.205.96-7.183 2.739a10.749 10.749 0 00-7.183-2.74H9.509c-4.003 0-7.247-3.21-7.247-7.177V2.25h73.491zM40.187 34.835a8.47 8.47 0 016.012-2.471h22.245c5.268 0 9.556-4.219 9.556-9.413V0H0v22.935c0 5.194 4.256 9.413 9.509 9.413h22.308c2.263 0 4.398.882 6.012 2.471L39.016 36l1.17-1.165z"
                fill="#FFFFFF"
            ></path>
            <path
                d="M67.522 11.676c0 .681-.556 1.177-1.255 1.177-.7 0-1.255-.496-1.255-1.177 0-.682.556-1.178 1.255-1.178.7-.03 1.255.465 1.255 1.178zm-2.352 9.622h2.178v-7.546H65.17v7.546zm-3.909-4.556l2.845 4.556h-2.368l-1.907-3.022-1.033 1.271v1.75h-2.161V10.453h2.16v5.004c0 .93-.11 1.86-.11 1.86h.047s.509-.821.938-1.41l1.653-2.154h2.542l-2.606 2.99zm-6.817-.278c0-1.875-.938-2.898-2.432-2.898-1.271 0-1.939.728-2.32 1.426h-.048l.112-1.24h-2.162v7.546h2.162V16.82c0-.868.524-1.472 1.335-1.472.81 0 1.16.527 1.16 1.534v4.416h2.177l.016-4.834zm-8.931-4.788c0 .681-.557 1.177-1.256 1.177-.7 0-1.255-.496-1.255-1.177 0-.682.556-1.178 1.255-1.178.715-.03 1.256.465 1.256 1.178zm-2.352 9.622h2.177v-7.546H43.16v7.546zm-3.75-2.107c0-.605-.859-.729-1.86-1.008-1.16-.294-2.622-.867-2.622-2.308 0-1.426 1.398-2.324 3.051-2.324 1.541 0 2.956.712 3.544 1.72l-1.86 1.022c-.19-.666-.762-1.193-1.62-1.193-.557 0-1.018.232-1.018.682 0 .573 1.018.635 2.162.991 1.208.372 2.32.915 2.32 2.294 0 1.518-1.446 2.417-3.115 2.417-1.811 0-3.242-.744-3.877-1.952l1.89-1.039c.24.822.922 1.441 1.955 1.441.62 0 1.05-.248 1.05-.743zm-6.882-8.677h-2.177v8.692c0 .775.175 1.348.509 1.705.35.356.89.526 1.636.526.255 0 .525-.03.78-.077.27-.062.476-.14.65-.233l.191-1.425a2.07 2.07 0 01-.46.124c-.128.03-.287.03-.461.03-.286 0-.414-.077-.509-.216-.111-.14-.159-.387-.159-.744v-8.382zm-7.246 4.57c-.795 0-1.446.558-1.621 1.581h3.05c.017-.899-.587-1.58-1.43-1.58zm3.353 3.007H23.63c.095 1.224.794 1.828 1.7 1.828.81 0 1.367-.527 1.494-1.24l1.828 1.007c-.54.961-1.7 1.798-3.322 1.798-2.16 0-3.75-1.472-3.75-3.951 0-2.464 1.62-3.951 3.703-3.951 2.081 0 3.464 1.44 3.464 3.486-.016.604-.111 1.023-.111 1.023zm-11.077 3.207h2.257V10.916h-2.257v4.107h-4.243v-4.091H11.06v10.366h2.256v-4.292h4.243v4.292z"
                fill="#FFFFFF"
            ></path>
        </svg>

            <h1>Haitaton Beta</h1>


        </div>
     </div>
    <div class="${properties.kcFormCardClass!}">
        <header class="${properties.kcFormHeaderClass!}">
            <#if realm.internationalizationEnabled  && locale.supported?size gt 1>
                <div id="kc-locale">
                    <div id="kc-locale-wrapper" class="${properties.kcLocaleWrapperClass!}">
                        <div class="kc-dropdown" id="kc-locale-dropdown">
                            <a href="#" id="kc-current-locale-link">${locale.current}</a>
                            <ul>
                                <#list locale.supported as l>
                                    <li class="kc-dropdown-item"><a href="${l.url}">${l.label}</a></li>
                                </#list>
                        </ul>
                    </div>
                </div>
            </div>
        </#if>
        <#if !(auth?has_content && auth.showUsername() && !auth.showResetCredentials())>
            <#if displayRequiredFields>
                <div class="${properties.kcContentWrapperClass!}">
                    <div class="${properties.kcLabelWrapperClass!} subtitle">
                        <span class="subtitle"><span class="required">*</span> ${msg("requiredFields")}</span>
                    </div>
                    <div class="col-md-10">
                        <h2 id="kc-page-title"><#nested "header"></h2>
                    </div>
                </div>
            <#else>
                <h2 id="kc-page-title"><#nested "header"></h2>
            </#if>
        <#else>
            <#if displayRequiredFields>
                <div class="${properties.kcContentWrapperClass!}">
                    <div class="${properties.kcLabelWrapperClass!} subtitle">
                        <span class="subtitle"><span class="required">*</span> ${msg("requiredFields")}</span>
                    </div>
                    <div class="col-md-10">
                        <#nested "show-username">
                        <div id="kc-username" class="${properties.kcFormGroupClass!}">
                            <label id="kc-attempted-username">${auth.attemptedUsername}</label>
                            <a id="reset-login" href="${url.loginRestartFlowUrl}">
                                <div class="kc-login-tooltip">
                                    <i class="${properties.kcResetFlowIcon!}"></i>
                                    <span class="kc-tooltip-text">${msg("restartLoginTooltip")}</span>
                                </div>
                            </a>
                        </div>
                    </div>
                </div>
            <#else>
                <#nested "show-username">
                <div id="kc-username" class="${properties.kcFormGroupClass!}">
                    <label id="kc-attempted-username">${auth.attemptedUsername}</label>
                    <a id="reset-login" href="${url.loginRestartFlowUrl}">
                        <div class="kc-login-tooltip">
                            <i class="${properties.kcResetFlowIcon!}"></i>
                            <span class="kc-tooltip-text">${msg("restartLoginTooltip")}</span>
                        </div>
                    </a>
                </div>
            </#if>
        </#if>
      </header>
      <div id="kc-content">
        <div id="kc-content-wrapper">

          <#-- App-initiated actions should not see warning messages about the need to complete the action -->
          <#-- during login.                                                                               -->
          <#if displayMessage && message?has_content && (message.type != 'warning' || !isAppInitiatedAction??)>
              <div class="alert-${message.type} ${properties.kcAlertClass!} pf-m-<#if message.type = 'error'>danger<#else>${message.type}</#if>">
                  <div class="pf-c-alert__icon">
                      <#if message.type = 'success'><span class="${properties.kcFeedbackSuccessIcon!}"></span></#if>
                      <#if message.type = 'warning'><span class="${properties.kcFeedbackWarningIcon!}"></span></#if>
                      <#if message.type = 'error'><span class="${properties.kcFeedbackErrorIcon!}"></span></#if>
                      <#if message.type = 'info'><span class="${properties.kcFeedbackInfoIcon!}"></span></#if>
                  </div>
                      <span class="${properties.kcAlertTitleClass!}">${kcSanitize(message.summary)?no_esc}</span>
              </div>
          </#if>

          <#nested "form">

            <#if auth?has_content && auth.showTryAnotherWayLink() && showAnotherWayIfPresent>
                <form id="kc-select-try-another-way-form" action="${url.loginAction}" method="post">
                    <div class="${properties.kcFormGroupClass!}">
                        <input type="hidden" name="tryAnotherWay" value="on"/>
                        <a href="#" id="try-another-way"
                           onclick="document.forms['kc-select-try-another-way-form'].submit();return false;">${msg("doTryAnotherWay")}</a>
                    </div>
                </form>
            </#if>

          <#if displayInfo>
              <div id="kc-info" class="${properties.kcSignUpClass!}">
                  <div id="kc-info-wrapper" class="${properties.kcInfoAreaWrapperClass!}">
                      <#nested "info">
                  </div>
              </div>
          </#if>
        </div>
      </div>
    </div>
  </div>
    <footer class="footer" role="contentinfo">
    <p>© Copyright 2020 • All rights reserved</p>
    <div class="Koros-module_koros__1r3rg">
        <svg xmlns="http://www.w3.org/2000/svg" aria-hidden="true" width="100%" height="85">
            <defs>
                <pattern id="koros_basic-1" x="0" y="0" width="106" height="85" patternUnits="userSpaceOnUse">
                    <path fill="#ffc61e" transform="scale(5.3)" d="M0,800h20V0c-4.9,0-5,2.6-9.9,2.6S5,0,0,0V800z">
                    </path>
                </pattern>
            </defs>
            <rect fill="url(#koros_basic-1)" width="100%" height="85">
            </rect>
        </svg>
    </div>
</footer>
</body>
</html>
</#macro>
