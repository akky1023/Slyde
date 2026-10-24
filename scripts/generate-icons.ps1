# SVG sources and Android VectorDrawables share the same 24x24 path geometry.
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
$svgDirectory=Join-Path $projectRoot 'prototype/artwork/icons'
$drawableDirectory=Join-Path $projectRoot 'prototype/src/main/res/drawable'
New-Item -ItemType Directory -Force $svgDirectory | Out-Null
$icons=[ordered]@{
    more='M4 10A2 2 0 1 0 4 14A2 2 0 1 0 4 10 M12 10A2 2 0 1 0 12 14A2 2 0 1 0 12 10 M20 10A2 2 0 1 0 20 14A2 2 0 1 0 20 10'
    expand='M6 9L12 15L18 9'
    collapse='M6 15L12 9L18 15'
    settings='M8.250 5.505L9.933 4.791L9.921 2.219L14.079 2.219L14.067 4.791L15.750 5.505L15.750 5.505L17.210 6.605L19.431 5.309L21.511 8.910L19.277 10.186L19.500 12.000L19.500 12.000L19.277 13.814L21.511 15.090L19.431 18.691L17.210 17.395L15.750 18.495L15.750 18.495L14.067 19.209L14.079 21.781L9.921 21.781L9.933 19.209L8.250 18.495L8.250 18.495L6.790 17.395L4.569 18.691L2.489 15.090L4.723 13.814L4.500 12.000L4.500 12.000L4.723 10.186L2.489 8.910L4.569 5.309L6.790 6.605L8.250 5.505Z M15.2 12A3.2 3.2 0 1 0 8.8 12A3.2 3.2 0 1 0 15.2 12'
    shift='M12 3L3 12H8V21H16V12H21Z'
    shift_on='M12 3L3 12H8V21H16V12H21Z'
    caps_lock_on='M12 2L3 11H8V17H16V11H21Z M8 20H16V22H8Z'
    caps_lock='M12 2L3 11H8V17H16V11H21Z M8 21H16'
    delete='M9 5H21V19H9L3 12Z M12 9L18 15 M18 9L12 15'
    enter='M20 5V14H4 M9 9L4 14L9 19'
    cursor_left='M19 12H5 M11 6L5 12L11 18 M3 5V19'
    cursor_right='M5 12H19 M13 6L19 12L13 18 M21 5V19'
    space='M4 9V16H20V9'
    history='M4 7V3 M4 7H8 M4 7A9 9 0 1 1 3 14 M12 7V12L16 14'
    common='M12 3L14.5 9.5L21 12L14.5 14.5L12 21L9.5 14.5L3 12L9.5 9.5Z'
    halfwidth='M9 3L7 21 M17 3L15 21 M4 8H21 M3 16H20'
    fullwidth='M8 4H4V20H8 M16 4H20V20H16 M10 8H14 M10 16H14'
    arrows='M3 8H21 M17 4L21 8L17 12 M21 16H3 M7 12L3 16L7 20'
    math='M3 7H11 M7 3V11 M15 5H22 M3 17H11 M15 15H22 M15 20H22'
    currency='M6 3L12 10L18 3 M12 10V21 M5 11H19 M5 16H19'
    units='M3 7H21V18H3Z M7 7V12 M11 7V10 M15 7V12 M19 7V10'
    shapes='M3 3H10V10H3Z M17 3L21 10H13Z M3 17A4 4 0 1 0 11 17A4 4 0 1 0 3 17 M17 13L21 17L17 21L13 17Z'
    face='M12 3A9 9 0 1 0 12 21A9 9 0 1 0 12 3 M8 9H8.1 M16 9H16.1 M7 14Q12 20 17 14'
    people='M12 3A4 4 0 1 0 12 11A4 4 0 1 0 12 3 M4 21V19Q4 13 12 13Q20 13 20 19V21'
    component='M12 3Q4 11 4 15A8 8 0 0 0 20 15Q20 11 12 3Z M7 15Q7 18 10 19'
    nature='M20 3Q3 2 4 14Q5 21 13 19Q22 17 20 3Z M3 22L16 8 M7 18V12 M10 15H16'
    food='M4 3V8Q4 12 10 8V3 M7 3V21 M20 3Q14 7 16 13H20 M20 3V21'
    travel='M3 10L10 11L10 4Q12 1 14 4V11L21 10V13L14 16V20L17 21V22L12 21L7 22V21L10 20V16L3 13Z'
    activity='M12 3A9 9 0 1 0 12 21A9 9 0 1 0 12 3 M12 8L16 11L14 16H10L8 11Z M12 3V8 M3 10L8 11 M6 19L10 16 M18 19L14 16 M21 10L16 11'
    objects='M8 17V15Q3 10 7 5Q12 0 17 5Q21 10 16 15V17Z M8 20H16 M10 23H14 M12 13V17'
    symbols='M12 20L4 12Q-1 5 6 3Q10 2 12 7Q14 2 18 3Q25 5 20 12Z'
    flags='M5 22V3 M5 4Q9 1 13 4Q17 7 21 4V15Q17 18 13 15Q9 12 5 15'
}
foreach($name in $icons.Keys){
    $pathData=$icons[$name]
    $svg='<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24"><path d="'+$pathData+'" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>'
    $vector='<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24"><path android:pathData="'+$pathData+'" android:fillColor="@android:color/transparent" android:strokeColor="#FFFFFF" android:strokeWidth="1.8" android:strokeLineCap="round" android:strokeLineJoin="round"/></vector>'
    if($name -in @('shift_on','caps_lock_on','more')){
        $svg='<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24"><path d="'+$pathData+'" fill="currentColor"/></svg>'
        $vector='<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24"><path android:pathData="'+$pathData+'" android:fillColor="#FFFFFF"/></vector>'
    }
    [IO.File]::WriteAllText((Join-Path $svgDirectory ($name+'.svg')),$svg,[Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllText((Join-Path $drawableDirectory ('ic_'+$name+'.xml')),$vector,[Text.UTF8Encoding]::new($false))
}
