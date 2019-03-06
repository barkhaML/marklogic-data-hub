import { Component, Input, Output, EventEmitter, ViewChild } from '@angular/core';
import { MatDialog } from '@angular/material';
import { NewStepDialogComponent } from './new-step-dialog.component';

@Component({
  selector: 'app-step',
  templateUrl: './step.component.html',
  styleUrls: ['./step.component.scss'],
})
export class StepComponent {
  @Input() step: any;
  @Input() databases: any;
  @Output() updateStep = new EventEmitter();

  showBody = true;
  constructor(
    public dialog: MatDialog
  ) {}
  toggleBody() {
    this.showBody = !this.showBody;
  }
  editSettingsClicked() {
    const dialogRef = this.dialog.open(NewStepDialogComponent, {
      width: '600px',
      data: {title: 'Edit Step', databases: this.databases, step: this.step}
    });

    dialogRef.afterClosed().subscribe(response => {
      if (response) {
        this.updateStep.emit(response);
      }
    });
  }

}
