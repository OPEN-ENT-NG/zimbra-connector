var gulp = require('gulp');
var webpack = require('webpack-stream');
var merge = require('merge2');
const replace = require('gulp-replace');
var clean = require('gulp-clean');
var args = require('yargs').argv;

var apps = ['zimbra'];
var apps_viewonly = ['apizimbra'];

if (args.module) {
    apps = [args.module];
}

gulp.task('drop-cache', function () {
    var streams = [];
    apps.forEach(function (app) {
        streams.push(gulp.src(['./' + app + '/src/main/resources/public/dist'], {read: false}).pipe(clean()))
        streams.push(gulp.src(['./' + app + '/build'], {read: false}).pipe(clean()))
    });
    return merge(streams);
});

gulp.task('webpack', ['drop-cache'], function () {
    var streams = [];
    apps.forEach(function (app) {
        streams.push(gulp.src('./' + app + '/src/main/resources/public')
            .pipe(webpack(require('./' + app + '/webpack.config.js')))
            .on('error', function handleError() {
                this.emit('end'); // Recover from errors
            })
            .pipe(gulp.dest('./' + app + '/src/main/resources/public/dist')))
    });
    return merge(streams);
});

gulp.task('build', ['webpack'], function () {
    var streams = [];
    apps.forEach(function (app) {
        streams.push(gulp.src("./" + app + "/src/main/resources/view-src/**/*.+(html|txt|json)")
          .pipe(replace('@@VERSION', Date.now()))
          .pipe(gulp.dest("./" + app + "/src/main/resources/view")));

        streams.push(gulp.src("./" + app + "/src/main/resources/public/dist/behaviours.js")
          .pipe(gulp.dest("./" + app + "/src/main/resources/public/js")));
    });

    apps_viewonly.forEach(function (app) {
        streams.push(gulp.src("./" + app + "/src/main/resources/view-src/**/*.+(html|txt|json)")
          .pipe(replace('@@VERSION', Date.now()))
          .pipe(gulp.dest("./" + app + "/src/main/resources/view")));
    });

    return merge(streams);
});