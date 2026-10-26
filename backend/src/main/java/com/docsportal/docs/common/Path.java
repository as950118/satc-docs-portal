package com.docsportal.docs.common;

public class Path {

    // API Commons
    public static final String API_PREFIX                   =   "/api";
    public static final String API_VERSION                  =   "/v1";
    public static final String API_COMMON                   =   API_PREFIX + API_VERSION;

    // Domain
    public static final String DOCS                         =   "/docs";
    public static final String AUTH                         =   "/auth";
    public static final String SPECS                        =   "/specs";


    // 회원 관리
    public static final String API_AUTH                     =   API_COMMON + AUTH;
    public static final String API_AUTH_ID                  =   API_AUTH + "/{userId}";


    // 문서(DOCS)
    public static final String API_DOCS                     =   API_COMMON + DOCS;
    public static final String API_DOCS_ID                  =   API_DOCS + "/{documentId}";
    public static final String API_DOCS_TREE                =   API_DOCS + "/tree";
    public static final String API_DOCS_TREE_FULL           =   API_DOCS + "/tree/full";
    public static final String API_DOCS_TREE_PARENT         =   API_DOCS_TREE + "/{parentId}";
    public static final String API_DOCS_ROOT                =   API_DOCS + "/root";
    public static final String API_DOCS_CHILDREN            =   API_DOCS + "/{parentId}/children";
    public static final String API_DOCS_FOLDER              =   API_DOCS + "/folder";
    public static final String API_DOCS_MULTIPLE            =   API_DOCS + "/multiple";
    public static final String API_DOCS_MOVE                =   API_DOCS + "/move";
    public static final String API_DOCS_POSITION            =   API_DOCS + "/position";
    public static final String API_DOCS_STATUS              =   API_DOCS + "/status";
    public static final String API_DOCS_SEARCH              =   API_DOCS + "/search";
    public static final String API_DOCS_STATISTICS          =   API_DOCS + "/statistics";
    public static final String API_DOCS_FOLDERS             =   API_DOCS + "/folders";
    public static final String API_DOCS_SYNC                =   API_DOCS + "/sync";


    // 스펙 워크플로우(SPECS)
    public static final String API_SPECS                    =   API_COMMON + SPECS;
    public static final String API_SPECS_ID                 =   API_SPECS + "/{specId}";
    public static final String API_SPECS_APPROVE            =   API_SPECS_ID + "/approve";
    public static final String API_SPECS_TEMPLATE_PRD       =   API_SPECS + "/templates/prd";

}
