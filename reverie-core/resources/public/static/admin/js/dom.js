(function(window){
    var $m_ready_functions = [];

    var $m = function(selector) {
        return $(window.parent.document.framemain.document).find(selector);
    };
    var $m_ready = function(fn) {
        $m_ready_functions.push(fn);
    };
    var $o = function(selector) {
        return $(window.parent.document.frameoptions.document).find(selector);
    };

    var $m_on_loaded = function() {
        for (var i = 0, ii = $m_ready_functions.length; i < ii; i++) {
            $m_ready_functions[i]();
        }
    };

    var __hide_options = function() {
        window.parent.frameoptions.frameElement.style.display = "none";
    };
    var __show_options = function() {
        window.parent.frameoptions.frameElement.style.display = "block";
    };
    var __hide_main = function() {
        window.parent.framemain.frameElement.style.display = "none";
    };
    var __show_main = function() {
        window.parent.framemain.frameElement.style.display = "block";
    };

    var hide_main = function() {
        __show_options();
        __hide_main();
    };
    var show_main = function() {
        __show_main();
        __hide_options();
    };
    var hide_options = function() {
        __show_main();
        __hide_options();
    };
    var show_options = function() {
        __show_options();
        __hide_main();
    };

    options_uri = function(uri) {
        window.parent.frameoptions.location = uri;
    };
    main_uri = function(uri) {
        window.parent.framemain.location = uri;
    };

    reload_main = function() {
        var href = window.parent.framemain.location.pathname;
        var search = window.parent.framemain.location.search;
        main_uri(href + search);
    };

    window.dom = {options_uri: options_uri,
                  main_uri: main_uri,
                  hide_main: hide_main,
                  show_main: show_main,
                  hide_options: hide_options,
                  show_options: show_options,
                  $o: $o,
                  $m: $m,
                  $m_ready: $m_ready,
                  $m_on_loaded: $m_on_loaded,
                  reload_main: reload_main
                 };

    window.parent.dom = window.dom;
    window.parent.$ = $;
})(window);
