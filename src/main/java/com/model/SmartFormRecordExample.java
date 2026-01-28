package com.model;

import java.util.ArrayList;
import java.util.List;

public class SmartFormRecordExample {
    protected String orderByClause;

    protected boolean distinct;

    protected List<Criteria> oredCriteria;

    public SmartFormRecordExample() {
        oredCriteria = new ArrayList<Criteria>();
    }

    public void setOrderByClause(String orderByClause) {
        this.orderByClause = orderByClause;
    }

    public String getOrderByClause() {
        return orderByClause;
    }

    public void setDistinct(boolean distinct) {
        this.distinct = distinct;
    }

    public boolean isDistinct() {
        return distinct;
    }

    public List<Criteria> getOredCriteria() {
        return oredCriteria;
    }

    public void or(Criteria criteria) {
        oredCriteria.add(criteria);
    }

    public Criteria or() {
        Criteria criteria = createCriteriaInternal();
        oredCriteria.add(criteria);
        return criteria;
    }

    public Criteria createCriteria() {
        Criteria criteria = createCriteriaInternal();
        if (oredCriteria.size() == 0) {
            oredCriteria.add(criteria);
        }
        return criteria;
    }

    protected Criteria createCriteriaInternal() {
        Criteria criteria = new Criteria();
        return criteria;
    }

    public void clear() {
        oredCriteria.clear();
        orderByClause = null;
        distinct = false;
    }

    protected abstract static class GeneratedCriteria {
        protected List<Criterion> criteria;

        protected GeneratedCriteria() {
            super();
            criteria = new ArrayList<Criterion>();
        }

        public boolean isValid() {
            return criteria.size() > 0;
        }

        public List<Criterion> getAllCriteria() {
            return criteria;
        }

        public List<Criterion> getCriteria() {
            return criteria;
        }

        protected void addCriterion(String condition) {
            if (condition == null) {
                throw new RuntimeException("Value for condition cannot be null");
            }
            criteria.add(new Criterion(condition));
        }

        protected void addCriterion(String condition, Object value, String property) {
            if (value == null) {
                throw new RuntimeException("Value for " + property + " cannot be null");
            }
            criteria.add(new Criterion(condition, value));
        }

        protected void addCriterion(String condition, Object value1, Object value2, String property) {
            if (value1 == null || value2 == null) {
                throw new RuntimeException("Between values for " + property + " cannot be null");
            }
            criteria.add(new Criterion(condition, value1, value2));
        }

        public Criteria andIdIsNull() {
            addCriterion("id is null");
            return (Criteria) this;
        }

        public Criteria andIdIsNotNull() {
            addCriterion("id is not null");
            return (Criteria) this;
        }

        public Criteria andIdEqualTo(Integer value) {
            addCriterion("id =", value, "id");
            return (Criteria) this;
        }

        public Criteria andIdNotEqualTo(Integer value) {
            addCriterion("id <>", value, "id");
            return (Criteria) this;
        }

        public Criteria andIdGreaterThan(Integer value) {
            addCriterion("id >", value, "id");
            return (Criteria) this;
        }

        public Criteria andIdGreaterThanOrEqualTo(Integer value) {
            addCriterion("id >=", value, "id");
            return (Criteria) this;
        }

        public Criteria andIdLessThan(Integer value) {
            addCriterion("id <", value, "id");
            return (Criteria) this;
        }

        public Criteria andIdLessThanOrEqualTo(Integer value) {
            addCriterion("id <=", value, "id");
            return (Criteria) this;
        }

        public Criteria andIdIn(List<Integer> values) {
            addCriterion("id in", values, "id");
            return (Criteria) this;
        }

        public Criteria andIdNotIn(List<Integer> values) {
            addCriterion("id not in", values, "id");
            return (Criteria) this;
        }

        public Criteria andIdBetween(Integer value1, Integer value2) {
            addCriterion("id between", value1, value2, "id");
            return (Criteria) this;
        }

        public Criteria andIdNotBetween(Integer value1, Integer value2) {
            addCriterion("id not between", value1, value2, "id");
            return (Criteria) this;
        }

        public Criteria andDocNameIsNull() {
            addCriterion("doc_name is null");
            return (Criteria) this;
        }

        public Criteria andDocNameIsNotNull() {
            addCriterion("doc_name is not null");
            return (Criteria) this;
        }

        public Criteria andDocNameEqualTo(String value) {
            addCriterion("doc_name =", value, "docName");
            return (Criteria) this;
        }

        public Criteria andDocNameNotEqualTo(String value) {
            addCriterion("doc_name <>", value, "docName");
            return (Criteria) this;
        }

        public Criteria andDocNameGreaterThan(String value) {
            addCriterion("doc_name >", value, "docName");
            return (Criteria) this;
        }

        public Criteria andDocNameGreaterThanOrEqualTo(String value) {
            addCriterion("doc_name >=", value, "docName");
            return (Criteria) this;
        }

        public Criteria andDocNameLessThan(String value) {
            addCriterion("doc_name <", value, "docName");
            return (Criteria) this;
        }

        public Criteria andDocNameLessThanOrEqualTo(String value) {
            addCriterion("doc_name <=", value, "docName");
            return (Criteria) this;
        }

        public Criteria andDocNameLike(String value) {
            addCriterion("doc_name like", value, "docName");
            return (Criteria) this;
        }

        public Criteria andDocNameNotLike(String value) {
            addCriterion("doc_name not like", value, "docName");
            return (Criteria) this;
        }

        public Criteria andDocNameIn(List<String> values) {
            addCriterion("doc_name in", values, "docName");
            return (Criteria) this;
        }

        public Criteria andDocNameNotIn(List<String> values) {
            addCriterion("doc_name not in", values, "docName");
            return (Criteria) this;
        }

        public Criteria andDocNameBetween(String value1, String value2) {
            addCriterion("doc_name between", value1, value2, "docName");
            return (Criteria) this;
        }

        public Criteria andDocNameNotBetween(String value1, String value2) {
            addCriterion("doc_name not between", value1, value2, "docName");
            return (Criteria) this;
        }

        public Criteria andDocIdIsNull() {
            addCriterion("doc_id is null");
            return (Criteria) this;
        }

        public Criteria andDocIdIsNotNull() {
            addCriterion("doc_id is not null");
            return (Criteria) this;
        }

        public Criteria andDocIdEqualTo(String value) {
            addCriterion("doc_id =", value, "docId");
            return (Criteria) this;
        }

        public Criteria andDocIdNotEqualTo(String value) {
            addCriterion("doc_id <>", value, "docId");
            return (Criteria) this;
        }

        public Criteria andDocIdGreaterThan(String value) {
            addCriterion("doc_id >", value, "docId");
            return (Criteria) this;
        }

        public Criteria andDocIdGreaterThanOrEqualTo(String value) {
            addCriterion("doc_id >=", value, "docId");
            return (Criteria) this;
        }

        public Criteria andDocIdLessThan(String value) {
            addCriterion("doc_id <", value, "docId");
            return (Criteria) this;
        }

        public Criteria andDocIdLessThanOrEqualTo(String value) {
            addCriterion("doc_id <=", value, "docId");
            return (Criteria) this;
        }

        public Criteria andDocIdLike(String value) {
            addCriterion("doc_id like", value, "docId");
            return (Criteria) this;
        }

        public Criteria andDocIdNotLike(String value) {
            addCriterion("doc_id not like", value, "docId");
            return (Criteria) this;
        }

        public Criteria andDocIdIn(List<String> values) {
            addCriterion("doc_id in", values, "docId");
            return (Criteria) this;
        }

        public Criteria andDocIdNotIn(List<String> values) {
            addCriterion("doc_id not in", values, "docId");
            return (Criteria) this;
        }

        public Criteria andDocIdBetween(String value1, String value2) {
            addCriterion("doc_id between", value1, value2, "docId");
            return (Criteria) this;
        }

        public Criteria andDocIdNotBetween(String value1, String value2) {
            addCriterion("doc_id not between", value1, value2, "docId");
            return (Criteria) this;
        }

        public Criteria andAdminPhoneNumbersIsNull() {
            addCriterion("admin_phone_numbers is null");
            return (Criteria) this;
        }

        public Criteria andAdminPhoneNumbersIsNotNull() {
            addCriterion("admin_phone_numbers is not null");
            return (Criteria) this;
        }

        public Criteria andAdminPhoneNumbersEqualTo(String value) {
            addCriterion("admin_phone_numbers =", value, "adminPhoneNumbers");
            return (Criteria) this;
        }

        public Criteria andAdminPhoneNumbersNotEqualTo(String value) {
            addCriterion("admin_phone_numbers <>", value, "adminPhoneNumbers");
            return (Criteria) this;
        }

        public Criteria andAdminPhoneNumbersGreaterThan(String value) {
            addCriterion("admin_phone_numbers >", value, "adminPhoneNumbers");
            return (Criteria) this;
        }

        public Criteria andAdminPhoneNumbersGreaterThanOrEqualTo(String value) {
            addCriterion("admin_phone_numbers >=", value, "adminPhoneNumbers");
            return (Criteria) this;
        }

        public Criteria andAdminPhoneNumbersLessThan(String value) {
            addCriterion("admin_phone_numbers <", value, "adminPhoneNumbers");
            return (Criteria) this;
        }

        public Criteria andAdminPhoneNumbersLessThanOrEqualTo(String value) {
            addCriterion("admin_phone_numbers <=", value, "adminPhoneNumbers");
            return (Criteria) this;
        }

        public Criteria andAdminPhoneNumbersLike(String value) {
            addCriterion("admin_phone_numbers like", value, "adminPhoneNumbers");
            return (Criteria) this;
        }

        public Criteria andAdminPhoneNumbersNotLike(String value) {
            addCriterion("admin_phone_numbers not like", value, "adminPhoneNumbers");
            return (Criteria) this;
        }

        public Criteria andAdminPhoneNumbersIn(List<String> values) {
            addCriterion("admin_phone_numbers in", values, "adminPhoneNumbers");
            return (Criteria) this;
        }

        public Criteria andAdminPhoneNumbersNotIn(List<String> values) {
            addCriterion("admin_phone_numbers not in", values, "adminPhoneNumbers");
            return (Criteria) this;
        }

        public Criteria andAdminPhoneNumbersBetween(String value1, String value2) {
            addCriterion("admin_phone_numbers between", value1, value2, "adminPhoneNumbers");
            return (Criteria) this;
        }

        public Criteria andAdminPhoneNumbersNotBetween(String value1, String value2) {
            addCriterion("admin_phone_numbers not between", value1, value2, "adminPhoneNumbers");
            return (Criteria) this;
        }

        public Criteria andAdminUserIdsIsNull() {
            addCriterion("admin_user_ids is null");
            return (Criteria) this;
        }

        public Criteria andAdminUserIdsIsNotNull() {
            addCriterion("admin_user_ids is not null");
            return (Criteria) this;
        }

        public Criteria andAdminUserIdsEqualTo(String value) {
            addCriterion("admin_user_ids =", value, "adminUserIds");
            return (Criteria) this;
        }

        public Criteria andAdminUserIdsNotEqualTo(String value) {
            addCriterion("admin_user_ids <>", value, "adminUserIds");
            return (Criteria) this;
        }

        public Criteria andAdminUserIdsGreaterThan(String value) {
            addCriterion("admin_user_ids >", value, "adminUserIds");
            return (Criteria) this;
        }

        public Criteria andAdminUserIdsGreaterThanOrEqualTo(String value) {
            addCriterion("admin_user_ids >=", value, "adminUserIds");
            return (Criteria) this;
        }

        public Criteria andAdminUserIdsLessThan(String value) {
            addCriterion("admin_user_ids <", value, "adminUserIds");
            return (Criteria) this;
        }

        public Criteria andAdminUserIdsLessThanOrEqualTo(String value) {
            addCriterion("admin_user_ids <=", value, "adminUserIds");
            return (Criteria) this;
        }

        public Criteria andAdminUserIdsLike(String value) {
            addCriterion("admin_user_ids like", value, "adminUserIds");
            return (Criteria) this;
        }

        public Criteria andAdminUserIdsNotLike(String value) {
            addCriterion("admin_user_ids not like", value, "adminUserIds");
            return (Criteria) this;
        }

        public Criteria andAdminUserIdsIn(List<String> values) {
            addCriterion("admin_user_ids in", values, "adminUserIds");
            return (Criteria) this;
        }

        public Criteria andAdminUserIdsNotIn(List<String> values) {
            addCriterion("admin_user_ids not in", values, "adminUserIds");
            return (Criteria) this;
        }

        public Criteria andAdminUserIdsBetween(String value1, String value2) {
            addCriterion("admin_user_ids between", value1, value2, "adminUserIds");
            return (Criteria) this;
        }

        public Criteria andAdminUserIdsNotBetween(String value1, String value2) {
            addCriterion("admin_user_ids not between", value1, value2, "adminUserIds");
            return (Criteria) this;
        }
    }

    public static class Criteria extends GeneratedCriteria {

        protected Criteria() {
            super();
        }
    }

    public static class Criterion {
        private String condition;

        private Object value;

        private Object secondValue;

        private boolean noValue;

        private boolean singleValue;

        private boolean betweenValue;

        private boolean listValue;

        private String typeHandler;

        public String getCondition() {
            return condition;
        }

        public Object getValue() {
            return value;
        }

        public Object getSecondValue() {
            return secondValue;
        }

        public boolean isNoValue() {
            return noValue;
        }

        public boolean isSingleValue() {
            return singleValue;
        }

        public boolean isBetweenValue() {
            return betweenValue;
        }

        public boolean isListValue() {
            return listValue;
        }

        public String getTypeHandler() {
            return typeHandler;
        }

        protected Criterion(String condition) {
            super();
            this.condition = condition;
            this.typeHandler = null;
            this.noValue = true;
        }

        protected Criterion(String condition, Object value, String typeHandler) {
            super();
            this.condition = condition;
            this.value = value;
            this.typeHandler = typeHandler;
            if (value instanceof List<?>) {
                this.listValue = true;
            } else {
                this.singleValue = true;
            }
        }

        protected Criterion(String condition, Object value) {
            this(condition, value, null);
        }

        protected Criterion(String condition, Object value, Object secondValue, String typeHandler) {
            super();
            this.condition = condition;
            this.value = value;
            this.secondValue = secondValue;
            this.typeHandler = typeHandler;
            this.betweenValue = true;
        }

        protected Criterion(String condition, Object value, Object secondValue) {
            this(condition, value, secondValue, null);
        }
    }
}