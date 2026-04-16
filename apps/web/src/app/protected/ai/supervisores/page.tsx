import { redirect } from "next/navigation";

export default function SupervisoresPage() {
    redirect("/protected/dashboard");
}
