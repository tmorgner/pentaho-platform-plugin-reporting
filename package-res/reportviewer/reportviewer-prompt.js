var pentaho = pentaho || {};
pentaho.reporting = pentaho.reporting || {};

pentaho.reporting.Prompt = function() {

  return {
    // The current render mode
    mode: 'INITIAL',

    _requiredModules: ['formatter', 'dojo'],

    /**
     * Inform the report viewer that a module has been loaded. When all required modules have been loaded the report
     * viewer will load itself.
     */
    moduleLoaded: function(name) {
      if (this.__requiredModules === undefined) {
        // Create a private required modules hash where the value represents if it has been loaded
        this.__requiredModules = {};
        $.each(this._requiredModules, function(i, m) {
          this.__requiredModules[m] = false; // Modules are by default not loaded
        }.bind(this));
      }
      this.__requiredModules[name] = true;

      var everythingLoaded = true;
      $.each(this.__requiredModules, function(i, m) {
        everythingLoaded &= m;
        return !everythingLoaded; // break when any module is not loaded
      });
      if (everythingLoaded) {
        this._load();
      }
    },

    _load: function() {
      dojo.require('pentaho.common.Messages');
      pentaho.common.Messages.addUrlBundle('reportviewer', '../../ws-run/ReportViewerLocalizationService/getJSONBundle');
    },

    /**
     * Create the prompt panel
     *
     * @param callback Function to call when panel is created
     */
    createPromptPanel: function() {
      this.fetchParameterDefinition(undefined, function(paramDefn) {
        this.panel = new pentaho.common.prompting.PromptPanel(
          'promptPanel',
          paramDefn);

        this.panel.submit = this.submit.bind(this);
        this.panel.schedule = this.schedule.bind(this);
        this.panel.getParameterDefinition = function() {
          var args = [];
          var promptPanel = arguments[0];
          args.push(promptPanel);
          args.push(arguments[1]); // callback
          args.push('USERINPUT');
          this.fetchParameterDefinition.apply(this, args);
        }.bind(this);

        // Provide our own text formatter
        this.panel.createDataTransportFormatter = ReportFormatUtil.createDataTransportFormatter.bind(ReportFormatUtil);
        this.panel.createFormatter = ReportFormatUtil.createFormatter.bind(ReportFormatUtil);

        // Provide our own i18n function
        this.panel.getString = pentaho.common.Messages.getString;

        this.initPromptPanel();
      }.bind(this), 'INITIAL');
    },

    initPromptPanel: function() {
      this.panel.init();
    },

    /**
     * Called when the submit button is pressed or an auto-submit happens.
     */
    submit: function(promptPanel) {
      alert('submit fired for panel: ' + promptPanel);
    },

    /**
     * Called when the schedule button is pressed.
     */
    schedule: function(promptPanel) {
      alert('schedule fired for panel: ' + promptPanel);
    },

    parameterParser: new pentaho.common.prompting.ParameterXmlParser(),
    parseParameterDefinition: function(xmlString) {
      // Provide a custom parameter normalization method unique to report viewer
      this.parameterParser.normalizeParameterValue = ReportFormatUtil.normalizeParameterValue.bind(ReportFormatUtil);
      return this.parameterParser.parseParameterXml(xmlString);
    },

    checkSessionTimeout: function(content, args) {
      if (content.status == 401 || this.isSessionTimeoutResponse(content)) {
        this.handleSessionTimeout(args);
        return true;
      }
      return false;
    },

    /**
     * @return true if the content is the login page.
     */
    isSessionTimeoutResponse: function(content) {
      if(content.indexOf('j_spring_security_check') != -1) {
        // looks like we have the login page returned to us
        return true;
      }
      return false;
    },

    /**
     * Prompts the user to relog in if they're within PUC, otherwise displays a dialog
     * indicating their session has expired.
     *
     * @return true if the session has timed out
     */
    handleSessionTimeout: function(args) {
      var callback = function() {
        var newParamDefn = this.fetchParameterDefinition.apply(this, args);
        this.panel.refresh(newParamDefn);
      }.bind(this);
      this.reauthenticate(callback);
    },

    reauthenticate: function(f) {
      if(top.mantle_initialized) {
        var callback = {
          loginCallback : f
        }
        window.parent.authenticate(callback);
      } else {
        this.showMessageBox(
          pentaho.common.Messages.getString('SessionExpiredComment'),
          pentaho.common.Messages.getString('SessionExpired'),
          pentaho.common.Messages.getString('OK'),
          undefined,
          undefined,
          undefined,
          true
        );
      }
    },

    /**
     * Loads the parameter xml definition from the server.
     * @param promptPanel panel to fetch parameter definition for
     * @param mode Render Mode to request from server: {INITIAL, MANUAL, USERINPUT}. If not provided, INITIAL will be used.
     */
    fetchParameterDefinition: function(promptPanel, callback, mode) {
      var options = this.getUrlParameters();
      // If we aren't passed a prompt panel this is the first request
      if (promptPanel) {
        $.extend(options, promptPanel.getParameterValues());
      }

      // Store mode so we can check if we need to refresh the report content or not in the view
      this.mode = mode;
      switch(mode) {
        case 'INITIAL':
        options['renderMode'] = 'PARAMETER';
          break;
        case 'USERINPUT':
          if (promptPanel == null || !promptPanel.getAutoSubmitSetting()) {
            options['renderMode'] = 'PARAMETER';
          } else {
            options['renderMode'] = 'XML';
          }
          break;
        default:
          options['renderMode'] = 'XML';
      }

      // Never send the session back. This is generated by the server.
      delete options['::session'];
      var args = arguments;
      var newParamDefn;
      var invokeCallback = true;
      $.ajax({
        async: false,
        cache: false,
        type: 'POST',
        url: this.getParameterUrl(),
        data: options,
        dataType:'text',
        success: function(xmlString) {
          if (this.checkSessionTimeout(xmlString, args)) {
            return;
          }
          try {
            newParamDefn = this.parseParameterDefinition(xmlString);

            if (mode === 'INITIAL' && newParamDefn.allowAutoSubmit()) {
              this.fetchParameterDefinition(undefined, callback);
              invokeCallback = false;
              return;
            }
            // Attempt to refetch with pagination

            // Make sure we retain the current auto-submit setting
            var currentAutoSubmit = promptPanel ? promptPanel.getAutoSubmitSetting() : undefined;
            if (currentAutoSubmit != undefined) {
              newParamDefn.autoSubmitUI = currentAutoSubmit;
            }
          } catch (e) {
            this.onFatalError(e);
          }
        }.bind(this),
        error: function(e) {
          if (this.checkSessionTimeout(e, args)) {
            return;
          }
          this.onFatalError(e);
          invokeCallback = false;
        }.bind(this)
      });
      if (invokeCallback) {
      callback(newParamDefn);
      }
    },

    getParameterUrl: function() {
      return '/pentaho/content/reporting';
    },

    getUrlParameters: function() {
      var urlParams = {};
      var e,
          a = /\+/g,  // Regex for replacing addition symbol with a space
          reg = /([^&=]+)=?([^&]*)/g,
          decode = function (s) { return decodeURIComponent(s.replace(a, " ")); },
          query = window.location.search.substring(1);

      while (e = reg.exec(query)) {
        var paramName = decode(e[1]);
        var paramVal = decode(e[2]);

        if (urlParams[paramName] !== undefined) {
          paramVal = $.isArray(urlParams[paramName]) 
            ? urlParams[paramName].concat([paramVal])
            : [urlParams[paramName], paramVal];
        }
        urlParams[paramName] = paramVal;
      }
      return urlParams;
    },

    getLocale: function() {
      var locale = this.getUrlParameters().locale;
      if (locale && locale.length > 2) {
        locale = locale.substring(0, 2);
      }
      return locale;
    },

    showMessageBox: function( message, dialogTitle, button1Text, button1Callback, button2Text, button2Callback, blocker ) {
      var messageBox = dijit.byId('messageBox');

      messageBox.setTitle(dialogTitle);
      messageBox.setMessage(message);
      
      if (blocker) {
        messageBox.setButtons([]);
      } else {
        var closeFunc = function() {
          Dashboards.hideProgressIndicator();
          messageBox.hide.call(messageBox);
        }

        if(!button1Text) {
          button1Text = pentaho.common.Messages.getString('OK');
        }
        if(!button1Callback) {
          button1Callback = closeFunc;
        }

        messageBox.onCancel = closeFunc;

        if(button2Text) {
          messageBox.callbacks = [
            button1Callback, 
            button2Callback
          ];
          messageBox.setButtons([button1Text,button2Text]);
        } else {
          messageBox.callbacks = [
            button1Callback 
          ];
          messageBox.setButtons([button1Text]);
        }
      }
      Dashboards.showProgressIndicator();
      messageBox.show();
    },

    /**
     * Called when there is a fatal error during parameter definition fetching/parsing
     *
     * @param e Error/exception encountered
     */
    onFatalError: function(e) {
      var errorMsg = pentaho.common.Messages.getString('ErrorParsingParamXmlMessage');
      if (console) {
        console.log(errorMsg + ": " + e);
      }
      this.showMessageBox(
        errorMsg,
        pentaho.common.Messages.getString('FatalErrorTitle'));
    }
  }
}