#!/usr/bin/env bash
sbt -Dplay.http.router=testOnlyDoNotUseInAppConf.Routes "run 7750"