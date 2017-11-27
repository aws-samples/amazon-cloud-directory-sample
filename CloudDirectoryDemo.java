//
//
//    Copyright 2017 Amazon.com, Inc. or its affiliates. All Rights Reserved.
//
//    Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except in compliance with the License. A copy of the License is located at
//
//        http://aws.amazon.com/apache2.0/
//
//    or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
    
/**
 * This is sample code for Amazon Cloud Directory. The sample code creates a schema, creates a directory, populates the directory 
 * with objects and then runs queries against the directory. At the end, all objects, schema and directory are deleted. 
 */

package clouddirectorydemo;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.clouddirectory.AmazonCloudDirectory;
import com.amazonaws.services.clouddirectory.AmazonCloudDirectoryClientBuilder;
import com.amazonaws.services.clouddirectory.model.*;

public class CloudDirectoryDemo {

	/**
	 * Define enums for types of group, employee role and office type. 
	 */

	// Defining group type for different levels of the organization 
    enum GroupType {
        ORGANIZATION("organization"),
        DEPARTMENT("department"),
        TEAM("team");

        final String value;

        GroupType(String value) {
            this.value = value;
        }
    }

    // Defining different job functions in the organization 
    enum EmployeeRole {
        CEO("ceo"), // in charge of organization
        DIRECTOR("director"), // in charge of department
        MANAGER("manager"), // in charge of team 
        SOFTWARE_DEVELOPMENT_ENGINEER("sde"),
        SOFTWARE_DEVELOPMENT_ENGINEER_IN_TEST("sdet"),
        DATA_SCIENTIST("datascientist");

        final String code;

        EmployeeRole(String code) {
            this.code = code;
        }

        String generateEmployeeId() {
            return String.format("%s-%s", code, new Random().nextInt(99999));
        }
    }

    // Defining different office types in the organization 
    enum OfficeType {
        HEADQUARTERS("headquarter"),
        ENGINEERING_OFFICE("engineering_office"),
        RESEARCH_OFFICE("research_office");

        final String code;

        OfficeType(String code) {
            this.code = code;
        }

        String generateOfficeId() {
            return String.format("%s-%s", code, new Random().nextInt(99999));
        }
    }

    private final AmazonCloudDirectory cloudDirectoryClient;
    
    private static final String SCHEMA_NAME = "Organization_Demo";  
    
    // Schema version is user-defined string. You can define a version identifier that is suitable for your use case
    private static final String SCHEMA_VERSION = "1.0"; 
    private static final String DIRECTORY_NAME = "Cloud_Corp"; // You can give a friendly name to each instance of your directory

    // Defining variables to hold ARN for different schema states. To learn more about schema states, please visit 
    // http://docs.aws.amazon.com/directoryservice/latest/admin-guide/cd_schemas.html 
    
    private String developmentSchemaArn;
    private String publishedSchemaArn;
    private String appliedSchemaArn;
    private String directoryArn;
    
    public static AWSCredentialsProvider awsCredential = null;

    public static void main(String[] args) 
    {
        System.out.println("Cloud Directory demo start");
	    try 
	    {
	    	// Please store your password or AWS secrets under your profile within your IDE. Please do not 
	    	// put the secrets within the source file, to avoid any misuse of your secrets. 
	    	// 
	    	awsCredential = new ProfileCredentialsProvider("chheda");
	    } catch (Exception e) 
	    {
	        throw new AmazonClientException(
	            "Cannot load the credentials from the credential profiles file. " +
	            "Please make sure that your credentials file is at the correct " +
	            "location, and is in valid format.",
	            e);
	    } // try-catch    
	    
        CloudDirectoryDemo demo = new CloudDirectoryDemo(); 

	    try 
	    {
            demo.buildSchema(); // This method is used to develop and publish a schema 
            demo.buildOrg(); // This method is used to populate a directory with employees
            demo.queryOrg(); // This method is used to query our data from the directory 
        }
	    catch (Exception e) 
	    {
	        throw new AmazonClientException(
	            "Cloud Directory Exception ",
	            e);
	    } // try-catch    
	    finally {
            demo.cleanUp(); // This method will clean (delete) all the data that we created in the program in Cloud Directory  
        }
    } // main

    // Constructor for Cloud Directory Demo 
    public CloudDirectoryDemo() //AWSCredentialsProvider awsCredential
    {
        cloudDirectoryClient = AmazonCloudDirectoryClientBuilder.standard()
                .withRegion(Regions.US_WEST_2)  // You can define a region specific to your needs 
                .withCredentials(awsCredential)
                .build();
    } // CloudDirectoryDemo

    /**
     * Build and publish schema used for this demo
     */
    private void buildSchema() {
        System.out.println(">> creating & building schema...");

        developmentSchemaArn = cloudDirectoryClient.createSchema(new CreateSchemaRequest().withName(SCHEMA_NAME)).getSchemaArn();

        //
        // There are 2 object types - Node and Leaf_Node
        // Leaf_Node can have multiple parents, Node cannot have multiple parents
        // To read more, please visit http://docs.aws.amazon.com/directoryservice/latest/admin-guide/cd_key_concepts.html 
        // 
        // "Group" facet
        cloudDirectoryClient.createFacet(new CreateFacetRequest()
                .withName("group_facet")
                .withSchemaArn(developmentSchemaArn)
                .withObjectType(ObjectType.NODE)   
                .withAttributes(getRequiredMutableStringAttributeWithNames("group_type")));

        // "Region" facet
        cloudDirectoryClient.createFacet(new CreateFacetRequest()
                .withName("region_facet")
                .withSchemaArn(developmentSchemaArn)
                .withObjectType(ObjectType.NODE));

        // "Office" facet
        cloudDirectoryClient.createFacet(new CreateFacetRequest()
                .withName("office_facet")
                .withSchemaArn(developmentSchemaArn)
                .withObjectType(ObjectType.NODE)
                .withAttributes(getRequiredMutableStringAttributeWithNames(
                        "office_id", "office_location", "office_type")));

        // "Employee" facet
        // Employee objects have been made as Leaf_Node so we can have multiple parents of type Node for these objects. 
        // We can assign group and location as 2 different parent types for employee. 
        //
        cloudDirectoryClient.createFacet(new CreateFacetRequest()
                .withName("employee_facet")
                .withSchemaArn(developmentSchemaArn)
                .withObjectType(ObjectType.LEAF_NODE)
                .withAttributes(getRequiredMutableStringAttributeWithNames(
                        "employee_id", "employee_name", "employee_role")));

        publishedSchemaArn = cloudDirectoryClient
        		.publishSchema(new PublishSchemaRequest()
        		.withDevelopmentSchemaArn(developmentSchemaArn)
                .withVersion(SCHEMA_VERSION))
        		.getPublishedSchemaArn();
        
        String schemaAsJson = cloudDirectoryClient
        		.getSchemaAsJson(new GetSchemaAsJsonRequest().withSchemaArn(publishedSchemaArn))
        		.getDocument();

        System.out.println(">> Schema in JSON: \n" + schemaAsJson);
    } // buildSchema

    /**
     * build directory with org data using schema
     */
    private void buildOrg() {
        /* BUILD DIRECTORY */

        System.out.println(">> Building directory and populating with data...");

        CreateDirectoryResult directory = cloudDirectoryClient.createDirectory(new CreateDirectoryRequest()
                .withName(DIRECTORY_NAME).withSchemaArn(publishedSchemaArn));
        directoryArn = directory.getDirectoryArn();
        appliedSchemaArn = directory.getAppliedSchemaArn();
        System.out.println(String.format(">> Directory ARN: %s", directoryArn));

        /* POPULATE SUBTREE UNDER "ORGANIZATION" */

        // Organization
        createGroup("/", "organization", GroupType.ORGANIZATION);
        // Research Department
        createGroup("/organization", "research", GroupType.DEPARTMENT);
        // Data science team
        createGroup("/organization/research", "data_mining", GroupType.TEAM);
        // Development department
        createGroup("/organization", "development", GroupType.DEPARTMENT);
        // DevOps team
        createGroup("/organization/development", "dev_ops", GroupType.TEAM);
        // QA team
        createGroup("/organization/development", "qa", GroupType.TEAM);

        // Management
        String gordon = createEmployee("/organization", "gordon h.", EmployeeRole.CEO);
        String herbert = createEmployee("/organization/development", "herbert i.", EmployeeRole.DIRECTOR);
        String irene = createEmployee("/organization/research", "irene j.", EmployeeRole.DIRECTOR);
        // Data mining team
        String john = createEmployee("/organization/research/data_mining", "john k.", EmployeeRole.MANAGER);
        String abbie = createEmployee("/organization/research/data_mining", "abbie b.", EmployeeRole.DATA_SCIENTIST);
        String bobbie = createEmployee("/organization/research/data_mining", "bobbie c.", EmployeeRole.DATA_SCIENTIST);
        // DevOps team
        String carl = createEmployee("/organization/development/dev_ops", "carl d.", EmployeeRole.MANAGER);
        String darryl = createEmployee("/organization/development/dev_ops", "darryl e.", EmployeeRole.SOFTWARE_DEVELOPMENT_ENGINEER);
        String edith = createEmployee("/organization/development/dev_ops", "edith f.", EmployeeRole.SOFTWARE_DEVELOPMENT_ENGINEER);
        // QA team
        String frank = createEmployee("/organization/development/qa", "frank g.", EmployeeRole.MANAGER);
        String kelly = createEmployee("/organization/development/qa", "kelly l.", EmployeeRole.SOFTWARE_DEVELOPMENT_ENGINEER_IN_TEST);
        String lauren = createEmployee("/organization/development/qa", "lauren m.", EmployeeRole.SOFTWARE_DEVELOPMENT_ENGINEER_IN_TEST);

        /* POPULATE SUBTREE UNDER "WORLD" */

        createRegion("/", "locations");

        // Offices in Seattle, Houston and New York City (NYC)
        createRegion("/locations", "americas");
        createRegion("/locations/americas", "usa");
        createOffice("/locations/americas/usa", "seattle", OfficeType.ENGINEERING_OFFICE);
        createOffice("/locations/americas/usa", "houston", OfficeType.ENGINEERING_OFFICE);
        createOffice("/locations/americas/usa", "nyc", OfficeType.HEADQUARTERS);

        // Cape Town office
        createRegion("/locations", "emea");
        createRegion("/locations/emea", "south_africa");
        createOffice("/locations/emea/south_africa", "cape_town", OfficeType.RESEARCH_OFFICE);

        /* LINK EMPLOYEES TO LOCATIONS */

        // DevOps team in Seattle
        linkEmployeeToOffice("/locations/americas/usa/seattle", carl);
        linkEmployeeToOffice("/locations/americas/usa/seattle", darryl);
        linkEmployeeToOffice("/locations/americas/usa/seattle", edith);
        
        // QA team in Houston
        linkEmployeeToOffice("/locations/americas/usa/houston", frank);
        linkEmployeeToOffice("/locations/americas/usa/houston", kelly);
        linkEmployeeToOffice("/locations/americas/usa/houston", lauren);
        
        // Management in NYC
        linkEmployeeToOffice("/locations/americas/usa/nyc", gordon);
        linkEmployeeToOffice("/locations/americas/usa/nyc", herbert);
        linkEmployeeToOffice("/locations/americas/usa/nyc", irene);
        
        // Researchers in Cape Town
        linkEmployeeToOffice("/locations/emea/south_africa/cape_town", john);
        linkEmployeeToOffice("/locations/emea/south_africa/cape_town", abbie);
        linkEmployeeToOffice("/locations/emea/south_africa/cape_town", bobbie);
    } // buildOrg


    /**
     * run various queries against the data
     */
    private void queryOrg() {

        /* LIST OF ALL OFFICES */

        // Recurse down from /locations, find all objects with "office_facet"
        Collection<ObjectReference> allOffices = recursiveList(path("/locations"),
                objectPath -> getFacetApplied(objectPath).getFacetName().equals("office_facet") );

        System.out.println(">> All offices: " + findParentPathsWithPrefix("/locations", allOffices));

        /* LIST OF ALL EMPLOYEES IN THE SEATTLE OFFICE */

        // All children of the Seattle office with employee_facet"
        Collection<ObjectReference> allEmployeesInSeattleOffice = recursiveList(path("/locations/americas/usa/seattle"),
                objectPath -> getFacetApplied(objectPath).getFacetName().equals("employee_facet"));

        System.out.println(">> All employees in the seattle office: " + findParentPathsWithPrefix("/locations", allEmployeesInSeattleOffice));

        /* LIST OF ALL OFFICES WITH SOFTWARE ENGINEERS & SOFTWARE ENGINEERS IN TEST */

        // Find all software engineers & software engineers in test
        Collection<ObjectReference> allSoftwareEngineers = recursiveList(path("/organization"), objectPath -> {
            String facetName = getFacetApplied(objectPath).getFacetName();
            List<AttributeKeyAndValue> attributes = getAttributes(objectPath);
            AttributeKeyAndValue sdeRole =
                    attributeKeyAndStringValue("employee_facet", "employee_role", EmployeeRole.SOFTWARE_DEVELOPMENT_ENGINEER.toString());
            AttributeKeyAndValue sdetRole =
                    attributeKeyAndStringValue("employee_facet", "employee_role", EmployeeRole.SOFTWARE_DEVELOPMENT_ENGINEER_IN_TEST.toString());

            return facetName.equals("employee_facet") && (attributes.contains(sdeRole) || attributes.contains(sdetRole));
        });
        System.out.println(">> All SDEs and SDETs: " + findParentPathsWithPrefix("/organization" , allSoftwareEngineers));

        // Find their offices and merge into a list of paths
        Set<String> allOfficesWithSe = findParentPathsWithPrefix("/locations",
                allSoftwareEngineers.stream().collect(Collectors.toList())).stream()
                    .map(path -> path.substring(0, path.lastIndexOf("/")))
                    .collect(Collectors.toSet());


        System.out.println(">> All offices with SDEs or SDETs: " + allOfficesWithSe);

        /* FIND EMPLOYEE BY NAME */

        // Create index
        cloudDirectoryClient.createIndex(new CreateIndexRequest()
                .withDirectoryArn(directoryArn)
                .withIsUnique(true)
                .withLinkName("employee_name_index")
                .withParentReference(path("/organization"))
                .withOrderedIndexedAttributeList(attributeKey("employee_facet", "employee_name")));

        // Get all employees
        Collection<ObjectReference> allEmployees = recursiveList(path("/organization"),
                objectPath -> getFacetApplied(objectPath).getFacetName().equals("employee_facet"));

        // Attach all employees to index
        attachAllToIndex("/organization/employee_name_index", allEmployees);

        // Use index on employee name to find employee 'herbert'
        String herbertObjectId = findEmployeeWithName("herbert i.").get(0).getObjectIdentifier();

        System.out.println(">> Employee 'herbert i.': "
                + findParentPathsWithPrefix("/organization", Collections.singleton(objectId(herbertObjectId))));
    } // queryOrg

    private void cleanUp() 
    {
        System.out.println(">> Cleaning up schema & directory...");

        try {
            cloudDirectoryClient.deleteSchema(new DeleteSchemaRequest().withSchemaArn(developmentSchemaArn));
        } catch (ResourceNotFoundException ignored) {}
        try {
            cloudDirectoryClient.deleteSchema(new DeleteSchemaRequest().withSchemaArn(publishedSchemaArn));
        } catch (ResourceNotFoundException ignored) {}

        cloudDirectoryClient.disableDirectory(new DisableDirectoryRequest().withDirectoryArn(directoryArn));
        cloudDirectoryClient.deleteDirectory(new DeleteDirectoryRequest().withDirectoryArn(directoryArn));
    } // cleanUp

    
/*****************************************************************************************************************
 * 
 * Helper functions
 * 
 ****************************************************************************************************************/
    
    private List<IndexAttachment> findEmployeeWithName(String prefix) {
        TypedAttributeValueRange prefixRange = new TypedAttributeValueRange()
                .withStartMode(RangeMode.INCLUSIVE)
                .withEndMode(RangeMode.INCLUSIVE)
                .withStartValue(new TypedAttributeValue().withStringValue(prefix))
                .withEndValue(new TypedAttributeValue().withStringValue(prefix));

        return cloudDirectoryClient.listIndex(new ListIndexRequest()
                .withDirectoryArn(directoryArn)
                .withIndexReference(path("/organization/employee_name_index"))
                .withRangesOnIndexedValues(new ObjectAttributeRange()
                        .withAttributeKey(attributeKey("employee_facet", "employee_name"))
                        .withRange(prefixRange))).getIndexAttachments();
    } // findEmployeeWithName

    private Set<String> findParentPathsWithPrefix(String pathPrefix, Collection<ObjectReference> roots) {
        return roots.stream().map(objectPath ->
                cloudDirectoryClient.listObjectParentPaths(new ListObjectParentPathsRequest()
                        .withDirectoryArn(directoryArn)
                        .withObjectReference(objectPath)).getPathToObjectIdentifiersList())
                .flatMap(List::stream)
                .map(PathToObjectIdentifiers::getPath)
                .filter(path -> path.startsWith(pathPrefix))
                .collect(Collectors.toSet());
    } // findParentPathsWithPrefix

    /**
     * recursively traverse through all children.
     *
     * @return list of child objects that satisfies the predicate.
     */
    private Collection<ObjectReference> recursiveList(ObjectReference object, Predicate<ObjectReference> filter) {
        HashSet<ObjectReference> res = new HashSet<>();
        if (filter == null || filter.test(object)) {
            res.add(object);
        }

        try {
            Map<String, String> children = children(object.getSelector());
            for (Map.Entry<String, String> child : children.entrySet()) {
                res.addAll(recursiveList(objectId(child.getValue()), filter));
            }
        } catch (NotNodeException ignored) {} // we've hit a leaf node

        return res;
    } // recursiveList

    // There are 2 different consistency levels - Serializable and Eventual
    // Serializable consistency level gives read-after-write consistency but they will have higher latencies
    // Eventual consistency level offer fast read operations (lower latencies) 
    // More information can be read at http://docs.aws.amazon.com/directoryservice/latest/admin-guide/consistencylevels.html 
    
    private Map<String, String> children(String objectPath) {
        return cloudDirectoryClient.listObjectChildren(new ListObjectChildrenRequest()
                .withDirectoryArn(directoryArn)
                .withConsistencyLevel(ConsistencyLevel.SERIALIZABLE) 
                .withMaxResults(10)
                .withObjectReference(path(objectPath))).getChildren();
    } // children


    /* API WRAPPERS */

    private void attachAllToIndex(String indexPath, Collection<ObjectReference> toAttach) {
        toAttach.stream().forEach(targetPath -> cloudDirectoryClient.attachToIndex(new AttachToIndexRequest()
                .withDirectoryArn(directoryArn)
                .withIndexReference(path(indexPath))
                .withTargetReference(targetPath)));
    } // attachAllToIndex

    /**
     * ! This method assumes that all objects have just one facet applied !
     *
     * @return the first element in the list of facets returned by getObjectInformation
     */
    private SchemaFacet getFacetApplied(ObjectReference ref) {
        return cloudDirectoryClient.getObjectInformation(new GetObjectInformationRequest()
                .withDirectoryArn(directoryArn)
                .withConsistencyLevel(ConsistencyLevel.EVENTUAL)
                .withObjectReference(ref)).getSchemaFacets().get(0);
    } // getFacetApplied

    private List<AttributeKeyAndValue> getAttributes(ObjectReference ref) {
        return cloudDirectoryClient.listObjectAttributes(new ListObjectAttributesRequest()
                .withDirectoryArn(directoryArn)
                .withConsistencyLevel(ConsistencyLevel.EVENTUAL)
                .withObjectReference(ref)).getAttributes();
    } // getAttributes

    private void linkEmployeeToOffice(String officePath, String employeePath) {
        String employeeId = employeePath.substring(employeePath.lastIndexOf("/") + 1, employeePath.length());
        System.out.println(String.format(">> assigning employee %s to office %s", employeeId, officePath));
        cloudDirectoryClient.attachObject(new AttachObjectRequest()
                .withDirectoryArn(directoryArn)
                .withLinkName(employeeId)
                .withParentReference(path(officePath))
                .withChildReference(path(employeePath)));
    } // linkEmployeeToOffice

    private void createOffice(String parentRegionPath, String officeLocation, OfficeType type) {
        String officeId = type.generateOfficeId();
        System.out.println(String.format(">> creating office at %s, location: %s, type: %s id: %s",
                parentRegionPath, officeLocation, type, officeId));
        cloudDirectoryClient.createObject(new CreateObjectRequest()
        		.withDirectoryArn(directoryArn)
                .withParentReference(path(parentRegionPath))
                .withLinkName(officeLocation)
                .withSchemaFacets(schemaFacet("office_facet"))
                .withObjectAttributeList(
                        attributeKeyAndStringValue("office_facet", "office_id", officeId),
                        attributeKeyAndStringValue("office_facet", "office_type", type.toString()),
                        attributeKeyAndStringValue("office_facet", "office_location", officeLocation)));
    } // createOffice

    private void createRegion(String parentRegionPath, String regionName) {
        System.out.println(String.format(">> creating region at %s/%s",
                parentRegionPath.equals("/") ? "" : parentRegionPath, regionName));
        cloudDirectoryClient.createObject(new CreateObjectRequest()
        		.withDirectoryArn(directoryArn)
                .withParentReference(path(parentRegionPath)).withLinkName(regionName)
                .withSchemaFacets(schemaFacet("region_facet")));
    } // createRegion

    private String createEmployee(String employeeGroupPath, String employeeName, EmployeeRole role) {
        String employeeId = role.generateEmployeeId();
        System.out.println(String.format(">> creating employee at %s, name: %s, role: %s id: %s",
                employeeGroupPath, employeeName, role, employeeId));
        cloudDirectoryClient.createObject(new CreateObjectRequest()
        		.withDirectoryArn(directoryArn)
                .withParentReference(path(employeeGroupPath))
                .withLinkName(employeeId)
                .withSchemaFacets(schemaFacet("employee_facet"))
                .withObjectAttributeList(
                        attributeKeyAndStringValue("employee_facet", "employee_role", role.toString()),
                        attributeKeyAndStringValue("employee_facet", "employee_id", employeeId),
                        attributeKeyAndStringValue("employee_facet", "employee_name", employeeName)));
        return employeeGroupPath + "/" + employeeId;
    } // createEmployee

    private void createGroup(String parentReference, String linkName, GroupType groupType) {
        System.out.println(String.format(">> creating group at %s/%s, type: %s",
                parentReference.equals("/")? "" : parentReference , linkName, groupType));
        cloudDirectoryClient.createObject(new CreateObjectRequest()
        		.withDirectoryArn(directoryArn)
                .withParentReference(path(parentReference))
                .withLinkName(linkName)
                .withSchemaFacets(schemaFacet("group_facet"))
                .withObjectAttributeList(attributeKeyAndStringValue("group_facet", "group_type", groupType.value)));
    } // createGroup

    /* POJO HELPERS */

    private ObjectReference path(String path) {
        return new ObjectReference().withSelector(path);
    } // path

    private ObjectReference objectId(String objectId) {
        return new ObjectReference().withSelector("$" + objectId);
    } // objectId

    private AttributeKeyAndValue attributeKeyAndStringValue(String facetName, String attributeName, String attributeValue) {
        return new AttributeKeyAndValue()
                .withKey(attributeKey(facetName, attributeName))
                .withValue(new TypedAttributeValue().withStringValue(attributeValue));
    } // attributeKeyAndStringValue
    
    private AttributeKey attributeKey(String facetName, String attributeName) {
        return new AttributeKey()
        		.withSchemaArn(appliedSchemaArn)
        		.withFacetName(facetName)
        		.withName(attributeName);
    } // attributeKey

    private SchemaFacet schemaFacet(String facetName) {
        return new SchemaFacet()
        		.withSchemaArn(appliedSchemaArn)
        		.withFacetName(facetName);
    } // schemaFacet

    private Collection<FacetAttribute> getRequiredMutableStringAttributeWithNames(String... attributeNames) {
        return Stream.of(attributeNames)
                .map(attributeName -> new FacetAttribute().withName(attributeName)
                        .withRequiredBehavior(RequiredAttributeBehavior.REQUIRED_ALWAYS)
                        .withAttributeDefinition(new FacetAttributeDefinition().withIsImmutable(false)
                                .withType(FacetAttributeType.STRING)))
                .collect(Collectors.toList());
    } // getRequiredMutableStringAttributeWithNames

}
