/*
 * Copyright 2008 Arne Limburg
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 */
package net.sf.jpasecurity.contacts.model;

import javax.annotation.security.RolesAllowed;
import javax.persistence.Basic;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;

import net.sf.jpasecurity.security.PermitWhere;

/**
 * @author Arne Limburg
 */
@Entity
@RolesAllowed("admin")
@PermitWhere("owner.name = CURRENT_PRINCIPAL")
public class Contact {

    @Id @GeneratedValue(strategy = GenerationType.AUTO)
    private int id;
    @ManyToOne
    private User owner;
    @Basic
    private String text;
    
    public Contact() {
    }
    
    public Contact(User user, String text) {
        setOwner(user);
        setText(text);
    }
    
    public int getId() {
        return id;
    }
    
    protected void setId(int id) {
        this.id = id;
    }
    
    public User getOwner() {
        return owner;
    }
    
    public void setOwner(User user) {
        this.owner = user;
    }

    public String getText() {
        return text;
    }
    
    public void setText(String text) {
        this.text = text;
    }
    
    public boolean equals(Object object) {
        if (!(object instanceof Contact)) {
            return false;
        }
        Contact contact = (Contact)object;
        return getOwner().equals(contact.getOwner()) && getText().equals(contact.getText());
    }
    
    public int hashCode() {
        return getOwner().hashCode() ^ getText().hashCode();
    }
}
