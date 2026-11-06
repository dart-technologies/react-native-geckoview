(function () {
  'use strict';

  if (window.__REACT_NATIVE_GECKOVIEW_BRIDGE__) {
    return;
  }
  window.__REACT_NATIVE_GECKOVIEW_BRIDGE__ = true;

  const EVAL_REQUEST_TYPE = 'reactNativeGeckoViewEval';
  const EVAL_RESULT_TYPE = 'reactNativeGeckoViewEvalResult';
  const NATIVE_APP_ID = 'react_native_geckoview';

  function serializeResult(value) {
    if (typeof value === 'undefined') {
      return 'undefined';
    }
    try {
      const json = JSON.stringify(value);
      return typeof json === 'undefined' ? 'undefined' : json;
    } catch (error) {
      return String(value);
    }
  }

  function errorMessage(error) {
    if (error && typeof error.message === 'string') {
      return error.message;
    }
    return String(error);
  }

  let port;
  try {
    port = browser.runtime.connectNative(NATIVE_APP_ID);
  } catch (_) {
    return;
  }

  port.onMessage.addListener((message) => {
    if (!message || message.type !== EVAL_REQUEST_TYPE) {
      return;
    }

    const requestId = String(message.requestId || '');
    if (!requestId) {
      return;
    }

    try {
      const result = eval(String(message.code || ''));
      port.postMessage({
        type: EVAL_RESULT_TYPE,
        requestId,
        result: serializeResult(result)
      });
    } catch (error) {
      port.postMessage({
        type: EVAL_RESULT_TYPE,
        requestId,
        error: errorMessage(error)
      });
    }
  });
})();
