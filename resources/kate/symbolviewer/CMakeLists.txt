add_library(katesymbolviewerplugin MODULE "")
target_compile_definitions(katesymbolviewerplugin PRIVATE TRANSLATION_DOMAIN="katesymbolviewer")
target_link_libraries(katesymbolviewerplugin PRIVATE KF5::TextEditor)

target_sources(
  katesymbolviewerplugin 
  PRIVATE
    flow_parser.cpp
    cpp_parser.cpp 
    tcl_parser.cpp 
    fortran_parser.cpp 
    perl_parser.cpp 
    php_parser.cpp 
    xslt_parser.cpp 
    xml_parser.cpp 
    ruby_parser.cpp 
    python_parser.cpp 
    bash_parser.cpp 
    ecma_parser.cpp 
    plugin_katesymbolviewer.cpp 
    plugin.qrc
)

kcoreaddons_desktop_to_json(katesymbolviewerplugin katesymbolviewerplugin.desktop)
install(TARGETS katesymbolviewerplugin DESTINATION ${PLUGIN_INSTALL_DIR}/ktexteditor)
install(TARGETS katesymbolviewerplugin DESTINATION ${CMAKE_INSTALL_PREFIX}/bin/ktexteditor )
