package org.springframework.samples.petclinic;

import java.util.Date;

/**
 * Simple JavaBean domain object representing a visit.
 *
 * @author Ken Krebs
 * @author Arne Limburg
 */
public class Visit extends BaseEntity {

	/** Holds value of property date. */
	private Date date;

	/** Holds value of property description. */
	private String description;

	/** Holds value of property pet. */
	private Pet pet;

    /** Holds value of property pet. */
    private Vet vet;

	/** Creates a new instance of Visit for the current date */
	public Visit() {
		this.date = new Date();
	}


	/** Getter for property date.
	 * @return Value of property date.
	 */
	public Date getDate() {
		return this.date;
	}

	/** Setter for property date.
	 * @param date New value of property date.
	 */
	public void setDate(Date date) {
		this.date = date;
	}

	/** Getter for property description.
	 * @return Value of property description.
	 */
	public String getDescription() {
		return this.description;
	}

	/** Setter for property description.
	 * @param description New value of property description.
	 */
	public void setDescription(String description) {
		this.description = description;
	}

	/** Getter for property pet.
	 * @return Value of property pet.
	 */
	public Pet getPet() {
		return this.pet;
	}

	/** Setter for property pet.
	 * @param pet New value of property pet.
	 */
	protected void setPet(Pet pet) {
		this.pet = pet;
	}

    /** Getter for property vet.
     * @return Value of property vet.
     */
    public Vet getVet() {
        return this.vet;
    }

    /** Setter for property vet.
     * @param pet New value of property vet.
     */
    public void setVet(Vet vet) {
        this.vet = vet;
    }
}