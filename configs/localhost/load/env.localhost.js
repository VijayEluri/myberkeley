define(["/dev/configuration/config.js"], function(config) {
    config.isDev = true;

    // Tracking and statistics
    config.Tracking = {
        GoogleAnalytics: {
            WebPropertyID : "UA-21809393-3"
        }
    };

});
