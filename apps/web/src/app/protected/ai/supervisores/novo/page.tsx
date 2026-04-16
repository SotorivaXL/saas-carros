import { redirect } from "next/navigation";

export default function NovoSupervisorPage() {
    redirect("/protected/dashboard");
}
