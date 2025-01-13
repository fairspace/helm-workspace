export const APPLICATION_NAME = 'Fairspace';
export const DEFAULT_METADATA_VIEW_MENU_LABEL = 'Metadata';
export const THE_HYVE_URL = 'https://thehyve.nl';
export const APPLICATION_DOCS_URL = 'https://docs.fairway.app/';
export const LOCAL_STORAGE_MENU_KEY = 'FAIRSPACE_MENU_EXPANDED';
export const CUT = 'CUT';
export const COPY = 'COPY';
export const PATH_SEPARATOR = '/';

// UI
export const LEFT_MENU_EXPANSION_DELAY = 500;
export const MAIN_CONTENT_WIDTH = '65%';
export const SIDE_PANEL_WIDTH = '35%';
export const MAIN_CONTENT_MAX_HEIGHT = 'calc(100vh - 156px)';
export const LEFT_PANEL_MAX_WIDTH = 305;
export const COLLECTIONS_PATH = 'collections';
export const METADATA_PATH = '/metadata';
export const CROSS_WORKSPACES_SEARCH_PATH = '/workspaces/_all';
export const TOOLTIP_ENTER_DELAY = 350;
export const DATE_FORMAT = 'dd-MM-yyyy';

// The maximum number of items in a list in the right panel, for performance reasons.
// If you change this, also change it in 'MetadataService.java'
export const MAX_LIST_LENGTH = 100;
// Max length of URL in the browser
export const MAX_URL_LENGTH = 2000;

// Metadata schemas
export const SHACL_NS = 'http://www.w3.org/ns/shacl#';
export const XMLSCHEMA_NS = 'http://www.w3.org/2001/XMLSchema#';
export const RDF_NS = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#';
export const RDFS_NS = 'http://www.w3.org/2000/01/rdf-schema#';
export const FAIRSPACE_NS = 'https://fairspace.nl/ontology#';
export const DASH_NS = 'http://datashapes.org/dash#';
export const RDF_TYPE = RDF_NS + 'type';

// URIs
export const TYPE_URI = RDF_NS + 'type';
export const LABEL_URI = RDFS_NS + 'label';
export const COMMENT_URI = RDFS_NS + 'comment';
export const SUBCLASS_URI = RDFS_NS + 'subClassOf';

export const RDFS_CLASS = RDFS_NS + 'Class';

export const SHACL_NAME = SHACL_NS + 'name';
export const SHACL_DESCRIPTION = SHACL_NS + 'description';
export const SHACL_PATH = SHACL_NS + 'path';
export const SHACL_INVERS_PATH = SHACL_NS + 'inversePath';
export const SHACL_TARGET_CLASS = SHACL_NS + 'targetClass';
export const SHACL_PROPERTY = SHACL_NS + 'property';
export const SHACL_CLASS = SHACL_NS + 'class';
export const SHACL_NODE = SHACL_NS + 'node';
export const SHACL_NODEKIND = SHACL_NS + 'nodeKind';
export const SHACL_DATATYPE = SHACL_NS + 'datatype';
export const SHACL_MAX_COUNT = SHACL_NS + 'maxCount';
export const SHACL_MIN_COUNT = SHACL_NS + 'minCount';
export const SHACL_MAX_LENGTH = SHACL_NS + 'maxLength';
export const SHACL_IN = SHACL_NS + 'in';
export const SHACL_IRI = SHACL_NS + 'IRI';
export const SHACL_ORDER = SHACL_NS + 'order';
export const SHACL_PREFIX = SHACL_NS + 'prefix';
export const SHACL_NAMESPACE = SHACL_NS + 'namespace';
export const SHACL_PREFIX_DECLARATION = SHACL_NS + 'PrefixDeclaration';
export const SHACL_NODE_SHAPE = SHACL_NS + 'NodeShape';

export const DASH_LIST_SHAPE = DASH_NS + 'ListShape';
export const DASH_SINGLE_LINE = DASH_NS + 'singleLine';

export const STRING_URI = XMLSCHEMA_NS + 'string';
export const BOOLEAN_URI = XMLSCHEMA_NS + 'boolean';
export const DATETIME_URI = XMLSCHEMA_NS + 'dateTime';
export const DATE_URI = XMLSCHEMA_NS + 'date';
export const TIME_URI = XMLSCHEMA_NS + 'time';
export const INTEGER_URI = XMLSCHEMA_NS + 'integer';
export const LONG_URI = XMLSCHEMA_NS + 'long';
export const DECIMAL_URI = XMLSCHEMA_NS + 'decimal';
export const MARKDOWN_URI = FAIRSPACE_NS + 'markdown';

export const MACHINE_ONLY_URI = FAIRSPACE_NS + 'machineOnly';
export const USER_URI = FAIRSPACE_NS + 'User';
export const COLLECTION_URI = FAIRSPACE_NS + 'Collection';
export const FILE_URI = FAIRSPACE_NS + 'File';
export const DIRECTORY_URI = FAIRSPACE_NS + 'Directory';
export const FILE_PATH_URI = FAIRSPACE_NS + 'filePath';
export const FILE_SIZE_URI = FAIRSPACE_NS + 'fileSize';
export const MD5_URI = FAIRSPACE_NS + 'md5';
export const CONTENT_TYPE_URI = FAIRSPACE_NS + 'contentType';
export const DATE_CREATED_URI = FAIRSPACE_NS + 'dateCreated';
export const CREATED_BY_URI = FAIRSPACE_NS + 'createdBy';
export const DATE_MODIFIED_URI = FAIRSPACE_NS + 'dateModified';
export const MODIFIED_BY_URI = FAIRSPACE_NS + 'modifiedBy';
export const DATE_DELETED_URI = FAIRSPACE_NS + 'dateDeleted';
export const DELETED_BY_URI = FAIRSPACE_NS + 'deletedBy';
export const EXTERNAL_LINK_URI = FAIRSPACE_NS + 'externalLink';
export const IMPORTANT_PROPERTY_URI = FAIRSPACE_NS + 'importantProperty';
export const DEFAULT_NAMESPACE_URI = FAIRSPACE_NS + 'defaultNamespace';
export const USABLE_IN_METADATA_URI = FAIRSPACE_NS + 'usableInMetadata';
export const DOMAIN_INCLUDES_URI = FAIRSPACE_NS + 'domainIncludes';
export const WORKSPACE_URI = FAIRSPACE_NS + 'Workspace';
export const WORKSPACE_STATUS_URI = FAIRSPACE_NS + 'status';
export const OWNED_BY_URI = FAIRSPACE_NS + 'ownedBy';
export const CAN_LIST_URI = FAIRSPACE_NS + 'canList';
export const CAN_READ_URI = FAIRSPACE_NS + 'canRead';
export const CAN_WRITE_URI = FAIRSPACE_NS + 'canWrite';
export const CAN_MANAGE_URI = FAIRSPACE_NS + 'canManage';
export const CAN_ADD_SHARED_METADATA_URI = FAIRSPACE_NS + 'canAddSharedMetadata';
export const CAN_VIEW_PUBLIC_METADATA_URI = FAIRSPACE_NS + 'canViewPublicMetadata';
export const CAN_VIEW_PUBLIC_DATA_URI = FAIRSPACE_NS + 'canViewPublicData';
export const CAN_QUERY_METADATA_URI = FAIRSPACE_NS + 'canQueryMetadata';
export const IS_ADMIN = FAIRSPACE_NS + 'isAdmin';
export const IS_SUPERADMIN = FAIRSPACE_NS + 'isSuperadmin';
export const IS_MEMBER_OF_URI = FAIRSPACE_NS + 'isMemberOf';
export const IS_MANAGER_OF_URI = FAIRSPACE_NS + 'isManagerOf';

export const NIL_URI = FAIRSPACE_NS + 'nil';
